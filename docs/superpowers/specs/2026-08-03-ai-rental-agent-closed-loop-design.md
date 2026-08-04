# 27公寓 AI 租房顾问闭环设计

> 实施分支：`agentRag`
>
> 现有基线：该分支已经完成 Java 21、Spring Boot 3.4.1、Spring AI 1.0.0、GLM 双 Key、PGvector 第二数据源、房源/文档知识入库、Admin 知识管理、`RoomSearchTool`、Redis 多轮历史和 SSE 对话。本文档在此基线上补齐可复现运行与预约业务闭环，不重新实现或替换已经验证的 RAG 管线。

## 1. 目标

在现有 27 公寓找房系统中交付一个可复现、可降级、可自动测试的 AI 租房顾问闭环：租客登录 H5 后，通过自然语言描述租房需求，Agent 查询真实可租房源并检索租房知识；租客选择房源和预约时间，系统生成预约草稿；只有在租客明确确认后才创建预约；创建结果能够在“我的预约”中查询。

项目必须能够通过 Docker Compose 启动所需服务，并提供脱敏初始化数据、健康检查、运行文档和端到端验收脚本。没有 GLM API Key 或模型服务不可用时，核心找房和预约闭环仍能通过本地规则降级运行。

## 2. 范围

### 2.1 本期包含

- 验证并保留现有 Java 21、Spring Boot 3.4.1、Spring AI 1.0.0 工程基线，补充 Maven Wrapper。
- MySQL、Redis、RabbitMQ、MinIO、PGvector、后端服务和 H5 的 Docker Compose 编排。
- 可重复执行的数据库结构与脱敏演示数据初始化。
- GLM OpenAI 兼容接口接入，API Key 仅从本地 `.env` 或环境变量读取。
- 多轮 AI 会话、实时房源查询、租房知识 RAG、引用返回和本地降级。
- 预约草稿、一次性确认令牌、幂等确认和预约结果查询。
- RabbitMQ 预约事件、有限重试、死信队列和可观测日志。
- H5 AI 顾问入口、对话消息、房源卡片、引用、预约草稿、确认和结果展示。
- 单元测试、集成测试、API 闭环测试和 H5 端到端测试。

### 2.2 本期不包含

- AI 自动签约、自动支付或自动取消合同。
- 新增或重构 Admin 端知识库能力；`agentRag` 已有的知识上传、切片、重建索引和房源同步接口继续保留。
- 多租户、计费、模型评测平台和独立微服务拆分。
- 将真实 API Key、手机号或生产数据提交到 Git。

## 3. 设计原则

- 模型负责理解、选择只读工具和组织语言；数据库写入由确定性业务服务负责。
- 房源、预约和用户数据只以 MySQL 查询结果为准，模型不得构造不存在的业务数据。
- 预约采用“生成草稿”和“明确确认”两阶段协议，聊天文本本身不能直接创建预约。
- 确认令牌绑定登录用户和草稿内容，10 分钟失效、只能消费一次。
- 外部模型失败不影响应用启动；降级响应必须明确标记运行模式。
- 所有基础设施、版本和初始化数据都必须可复现，不能依赖开发者电脑上的已有服务。
- 保留现有 `.idea/misc.xml` 修改，不使用 `git reset --hard`、`git checkout --` 或覆盖式回滚。

## 4. 总体架构

### 4.1 客户端

`rentHouseH5` 继续使用 Vue 3、TypeScript、Vite、Vant、Pinia 和现有 Axios 登录态。新增 AI 顾问入口和页面，复用现有房源详情、预约页面与“我的预约”页面。

H5 不解析模型工具调用，只消费后端返回的稳定 DTO：会话消息、推荐房源、知识引用、预约草稿、确认令牌和预约结果。

### 4.2 后端

`web-app` 以现有 `RentalChatServiceImpl`、`ChatClientConfiguration`、`RoomSearchTool`、`VectorStore` 和 SSE 协议为基线，新增或调整以下职责边界：

- `RentalChatService`：保留 SSE 流式协议，校验请求、绑定登录用户会话、执行模型或 fallback 引擎并发送结构化事件。
- `ModelRentalChatEngine`：复用现有 Spring AI `ChatClient`、`RoomSearchTool` 和 PGvector 检索，调用 GLM 并流式生成回答。
- `FallbackRentalChatEngine`：在 GLM 或 PGvector 不可用时，复用现有 MySQL 房源查询并对版本化 Markdown 执行关键词检索。
- `RoomSearchTool`：保留现有 `@Tool`，继续只返回 MySQL 中真实可租房源，并补足结构化推荐 DTO。
- `RentalKnowledgeService`：复用现有房源/文档向量管线，在 fallback 下提供同引用 DTO 的关键词检索。
- `AppointmentDraftService`：校验房源、时间和联系人，创建 Redis 草稿及确认令牌。
- `AppointmentConfirmationService`：原子消费令牌，幂等创建预约并返回结果。
- `AppointmentEventPublisher`：通过 Transactional Outbox 发布预约事件；预约与 Outbox 在同一 MySQL 事务写入，后台任务投递成功后更新事件状态。
- `FallbackRentalAgent`：在未配置 GLM 或调用异常时执行规则意图识别、房源查询和关键词知识检索。

### 4.3 数据存储

- MySQL：用户、公寓、房间、租约、预约、Agent 幂等记录和预约事件 Outbox。
- Redis：会话历史缓存、预约草稿、一次性确认令牌和短期幂等结果。
- PGvector：版本化知识文档、切片、向量、来源和内容校验和。
- RabbitMQ：预约创建事件、通知事件、重试队列和死信队列。
- MinIO：保留现有房源图片能力；演示数据使用初始化 bucket 和固定演示图片。

## 5. 核心流程

### 5.1 登录

`demo` 配置下，固定演示账号可使用固定验证码登录；该能力必须通过 `app.demo-login.enabled=true` 显式开启。非 demo 配置继续使用短信验证码，不允许固定验证码生效。

### 5.2 找房与知识问答

1. H5 调用现有 `POST /app/ai/chat` SSE 接口，提交 `conversationId`、`message` 和可选结构化偏好。
2. 后端验证登录用户和消息长度，加载该用户有权访问的会话历史。
3. GLM 决定是否调用 `search_available_rooms` 和 `search_rental_knowledge`。
4. 房源工具查询 MySQL；知识工具查询 PGvector。两者均限制返回数量并设置超时。
5. Agent 只根据工具结果生成答案，后端同时返回结构化房源和引用。
6. 消息和工具摘要写入会话存储。H5 可通过会话查询接口恢复历史。
7. GLM 未配置、超时、限流或返回不可解析内容时，切换到本地规则 Agent，并在响应中返回 `mode=FALLBACK`。

### 5.3 RAG 入库与检索

租房知识以版本化 Markdown 文件纳入 Git。正常模型模式沿用 `agentRag` 已实现的文档上传、Tika 解析、切片、embedding、PGvector 入库和重建索引；Docker 演示环境在首次启动时幂等导入一份固定知识文档，重复启动不得产生重复向量。

正常模式使用现有 GLM embedding 和 PGvector 余弦相似度检索，返回 `title`、`category`、`source`、`snippet` 和 `score`。没有 embedding 能力时，使用本地版本化文档关键词评分，仍返回相同引用 DTO。

### 5.4 预约草稿与确认

1. 用户在推荐房源卡片中选择“预约看房”，填写预约时间和联系人。
2. H5 调用 `POST /app/ai/appointments/draft`。
3. 后端校验房源存在且可租、预约时间晚于当前时间且在允许范围内、联系人字段合法、当前用户没有同房源同时间段的有效预约。
4. 后端创建不可由客户端修改的规范化草稿，将其以用户 ID 为命名空间写入 Redis，TTL 为 10 分钟，并返回随机 `confirmationToken` 和脱敏摘要。
5. 用户点击确认后，H5 调用 `POST /app/ai/appointments/confirm`。
6. 后端原子锁定令牌，重新校验房源和重复预约，在事务中写入预约和幂等记录。
7. 相同用户使用同一令牌重复确认时返回第一次创建的预约；其他用户使用该令牌返回无权限错误。
8. 事务中同时写入预约事件 Outbox；提交后由发布任务投递 RabbitMQ，成功后标记已发布，失败则按退避策略重试。H5 展示预约 ID 和状态，并可跳转现有“我的预约”页面验证结果。

## 6. API 合同

### 6.1 AI 对话

`POST /app/ai/chat` 保持现有 `text/event-stream` SSE 返回方式，避免破坏已经实现的接口合同。

请求包含 `conversationId`、`message` 和可选结构化偏好。SSE `chat` 事件中的 `ChatSseEvent` 包含：

- `type=meta`：`conversationId` 和 `mode`（`MODEL` 或 `FALLBACK`）。
- `type=message`：流式文本片段。
- `type=recommendations`：真实房源结构化列表。
- `type=citations`：知识和房源引用。
- `type=done`：完整回答和建议动作。
- `type=error`：可向用户展示的错误，不包含密钥或供应商响应体。

Redis 历史 key 必须同时包含登录用户 ID 和 `conversationId`，防止用户通过猜测 ID 读取或污染他人上下文。本期不新增 MySQL 长期会话表。

### 6.2 预约草稿

`POST /app/ai/appointments/draft`

请求字段为 `sessionId`、`roomId`、`appointmentTime`、`contactName`、`contactPhone` 和可选 `additionalInfo`。响应字段为 `confirmationToken`、`expiresAt` 和脱敏后的 `draft`。

### 6.3 预约确认

`POST /app/ai/appointments/confirm`

请求只包含 `confirmationToken`。响应包含 `appointmentId`、`appointmentStatus`、`createdAt` 和 `idempotentReplay`。

所有接口沿用现有 `Result<T>` 外层协议和 `access-token` 认证头。

## 7. 错误处理与降级

- 参数错误：返回统一业务错误码，不调用模型或工具。
- 会话越权：返回无权限，不能泄露会话是否存在。
- GLM 缺少 Key、超时、限流或 5xx：记录不含敏感内容的诊断日志，执行本地 Agent。
- PGvector 不可用：切换关键词知识检索；房源查询仍可工作。
- MySQL 不可用：健康检查失败，应用不对外标记 ready。
- Redis 不可用：对话可退化为无历史单轮模式，但预约草稿接口返回“确认服务暂不可用”，禁止绕过令牌直接创建。
- RabbitMQ 不可用：预约事务仍提交，Outbox 保留待发布事件并由恢复任务重试；不得仅打印异常后丢失事件。
- 令牌过期、已消费或不属于当前用户：返回明确业务错误，不创建预约。
- 重复预约：返回已有预约信息或明确冲突，不产生第二条记录。
- H5 网络失败：保留用户输入和草稿，允许重试；确认按钮提交期间禁用，防止连续点击。

## 8. 基础设施与配置

项目根目录提供：

- `compose.yaml`：编排 MySQL、Redis、RabbitMQ、MinIO、PGvector、web-app 和 H5。
- `.env.example`：列出无秘密的配置键和演示默认值。
- 本地 `.env`：保存 GLM Key，必须被 Git 忽略。
- Maven Wrapper：固定 Maven 版本并使用 Java 21 构建镜像。
- 容器健康检查和 `depends_on.condition=service_healthy`。
- MySQL 与 PGvector 初始化脚本、MinIO bucket 初始化和脱敏演示图片。

默认启动命令为 `docker compose up --build`。README 必须记录首次启动、重置演示数据、运行测试、验证健康状态和执行演示流程的准确命令。

## 9. 数据初始化

从本机现有 `lease` 数据库结构整理可提交脚本，但不直接提交数据库导出中的真实用户数据。演示数据至少包含：

- 一个可固定验证码登录的租客账号。
- 两个区域、三套公寓、六个房间。
- 不同租金、付款方式、标签、图片、上架状态和租约状态。
- 至少一套不可租房源，用于验证过滤逻辑。
- 租房押金、付款、预约、入住、维修和退租知识文档。

初始化脚本必须可重复执行，空数据卷首次启动后即可完成 H5 找房和预约演示。

## 10. 测试设计

### 10.1 后端单元测试

- 请求校验、结构化偏好合并和本地意图解析。
- 房源工具只返回真实可租房源并限制数量。
- 知识切片、校验和、向量检索映射和关键词降级。
- 草稿字段校验、TTL、用户绑定和脱敏。
- 确认令牌一次性消费、越权拒绝、过期拒绝和幂等重放。
- 模型异常时切换 fallback，模型正常时保留结构化工具结果。

### 10.2 后端集成测试

使用 Testcontainers 启动 MySQL、Redis、RabbitMQ 和 PGvector，验证：

- Flyway/初始化迁移可从空库执行。
- 默认本地 profile 将现有 `lease` 结构登记为 V1 基线并仅执行 V2+ 增量迁移；隔离的 Docker profile 对空库执行 V1+，两套环境保持相同最终结构。
- 登录、对话、房源查询、草稿、确认和预约查询完整链路。
- 事务提交后事件发布，失败事件能够重试或进入死信处理。
- 同一确认令牌并发提交只创建一条预约。
- 不同用户不能读取会话或确认他人的草稿。

模型集成测试使用可控的本地 HTTP stub，不依赖真实 GLM 网络；单独提供可选的 GLM smoke test，由环境变量显式开启。

### 10.3 H5 测试

- TypeScript 类型检查和生产构建。
- 对话消息、fallback 标记、引用和房源卡片组件测试。
- Playwright 端到端测试：登录、发送混合问题、选择房源、生成草稿、确认预约、进入“我的预约”并看到新记录。
- 桌面和移动视口截图检查，确保输入框、确认弹窗、房源卡片和底部导航无重叠。

## 11. 验收标准

以下条件全部满足才算完成：

1. 全新 Docker 数据卷执行 `docker compose up --build` 后，所有必需服务健康。
2. 未设置 GLM Key 时能够完成登录、找房、知识问答、预约草稿、确认和预约查询。
3. 设置有效 GLM Key 时 SSE `meta` 事件为 `mode=MODEL`，并实际发生现有 `RoomSearchTool` 受控只读工具调用。
4. 推荐房源全部来自初始化 MySQL 数据，知识回答包含可定位的真实引用。
5. 未经确认不能创建预约；重复确认只产生一条预约；越权确认失败。
6. RabbitMQ 暂时不可用时预约不丢失，服务恢复后事件最终被处理。
7. 后端单元测试、集成测试、H5 类型检查、生产构建和 Playwright 闭环测试全部通过。
8. README 中的命令在当前仓库可直接执行，不依赖未记录的本机配置。
9. Git 中不存在真实 API Key、真实用户数据或无关 IDE 文件改动。

## 12. 实施边界

实施在当前 `master` 分支进行。设计文档、实施计划和每个可独立验证的功能阶段分别提交。提交只包含当前阶段相关文件；现有用户修改不纳入提交。每个阶段遵循测试先行，先观察目标测试失败，再写最小实现并运行全量相关回归测试。
