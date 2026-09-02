# 27公寓 Agent + RAG 面试版闭环实施计划

> 计划状态：执行中（用户已批准，按计划直接开发）
>
> 目标分支：`agentRag`
>
> 规格文档：`docs/superpowers/specs/2026-09-02-rental-agent-harness-rag-design.md`
>
> 交付性质：可运行的面试 Demo，不是生产投产方案

## 1. 目标

在当前已有的 27 公寓业务、AI 对话、RAG、预约和 Outbox 基础上，先补齐后端可解释的 Agent Harness，并把 RAG 检索链路、业务工具、预约确认和 API/SSE 契约统一为一条可复现闭环。H5 只作为后续客户端，不纳入本阶段开发和验收。

最终必须同时支持两条路径：

```text
无模型 Key -> Fallback Agent -> MySQL 房源 + 本地知识 -> 预约闭环
有模型 Key -> Luna/兼容模型 Agent Loop -> 领域工具 + RAG -> 预约闭环
```

## 2. 执行规约

### 2.1 分支和文件保护

- 每个 Task 开始前执行 `git branch --show-current`，输出必须是 `agentRag`。
- 不切换到 `master`，不对 `master` 做 merge、rebase 或强制覆盖。
- 开始前记录 `git status --short`，保留用户已有修改。
- `.idea/misc.xml` 永远不加入暂存区。
- 禁止 `git reset --hard`、`git checkout --`、删除式回滚和覆盖用户目录。
- 本阶段不复制、修改或构建 `E:\frontend\rentHouseH5`，也不修改 Admin 前端。
- 本阶段通过后端 API、SSE 测试客户端和自动化测试完成闭环；H5 作为后续客户端接入。

### 2.2 数据库保护

- 不启动 Docker，除非用户另行明确要求；文档和代码可先完成，运行验证以当前本地可用服务为准。
- 先读取本地数据库连接配置、Flyway history、表结构、索引和关键数据，再写 SQL。
- 绝不 `DROP TABLE`、`TRUNCATE`、重建现有业务表或清理未知数据。
- 只有确实缺字段、缺索引或缺辅助表时才新增 `V5+` 增量迁移。
- 如果现有库已经具备字段，直接复用 Mapper，不创建重复表。
- 所有演示 seed 使用独立 ID 和幂等写入，不覆盖非演示记录。

### 2.3 代码执行方式

- 每个 Task 遵循 `RED -> GREEN -> REFACTOR -> 单独提交`。
- 先写能描述失败行为的测试，再写实现。
- 每个 Task 完成后更新本文档 checkbox、规格文档的完成状态和测试记录。
- 遇到数据库结构与代码假设不一致时暂停当前 Task，先补充事实记录，不能猜字段。
- 不声称 Docker、真实模型或 Playwright 通过，除非本轮有新鲜命令输出。

## 3. 现有基线和待改造点

### 3.1 已有基线

- `RentalChatServiceImpl` 已支持模型/fallback 选择、Redis 历史和 SSE。
- `RoomSearchTool` 已查询真实 MySQL 房源。
- PGvector 和本地 Markdown 知识已经存在。
- 预约草稿、确认、幂等和 Outbox 已有实现和测试雏形。
- H5 已有 AI 入口和预约结果相关页面基础，但本阶段不修改前端。

### 3.2 本计划真正要补齐的内容

- 明确的 `RentalAgentRuntime`、`AgentContext`、`ToolRegistry`、权限策略和调用轨迹。
- 统一领域工具输入输出，避免模型结果和业务真值混在字符串里。
- RAG 的结构化 chunk 元数据、混合检索、去重、rerank 和引用。
- primary/fallback 模型配置和有限重试策略，优先适配 Luna 或 OpenAI 兼容模型。
- Agent 与现有预约草稿之间的安全边界。
- 面向面试的轨迹展示、RAG 小型评测集、闭环脚本和文档。

## 4. 任务总览

| Task | 内容 | 依赖 | 验收产物 |
| --- | --- | --- | --- |
| 0 | 基线、数据库和接口事实盘点 | 无 | 基线报告、无破坏证明 |
| 1 | Agent/RAG 契约和模型配置 | 0 | DTO、配置、契约测试 |
| 2 | RAG 文档元数据和结构化切片 | 1 | 可重复索引、chunk 测试 |
| 3 | 混合检索、改写、rerank、引用 | 2 | 检索服务和质量测试 |
| 4 | Agent Loop、工具注册和权限 | 1、3 | Agent Runtime 和轨迹测试 |
| 5 | 业务闭环接入和预约安全边界 | 4 | 草稿、确认、幂等和 Outbox 测试 |
| 6 | 后端 API/SSE 闭环集成验收 | 5 | API 闭环脚本、集成测试 |
| 7 | RAG 评测、面试文档和简历素材 | 6 | 评测集、后端演示文档、验收报告 |
| 8 | 最终审计和交付 | 7 | 分支、迁移、秘密和测试审计 |

---

## Task 0：审计基线和本地数据库事实

**目标：** 先把“已有的是什么”记录清楚，阻止后续误建表、误改 Mapper 和误判测试结果。

**预期文件：**

- Create: `docs/ai-rental-agent/baseline-2026-09-02.md`
- Inspect only: `web/web-app/src/main/resources/db/migration/**`
- Inspect only: `web/web-app/src/main/resources/application*.yml`
- Inspect only: 现有本地 MySQL 表结构和 Flyway history

**实施步骤：**

1. 确认当前分支和工作区状态。
2. 盘点现有 AI 类、Mapper、Controller、RAG 服务、预约服务和 H5 API。
3. 使用只读 SQL 记录现有表、列、索引、Flyway 版本和关键演示数据数量。
4. 对照 `V1` 到当前最高版本，标记已存在能力和真实缺口。
5. 输出“无需新增迁移”的字段，以及确实需要 `V5+` 的变更候选。

**自动验证：**

```powershell
git branch --show-current
git status --short
rg -n "CREATE TABLE|ALTER TABLE|@TableName|class Rental|class .*Appointment|VectorStore" web common model -g '*.java' -g '*.sql'
git diff --check
```

**手工验证：**

- 确认没有执行任何写库 SQL。
- 确认 `.idea/misc.xml` 仍是用户原有修改。
- 确认基线报告里没有把已有表列为“新建”。

**停止条件：** 无法连接本地数据库或无法确认表结构时，暂停所有迁移和 Mapper 开发，只完成代码静态盘点。

- [x] Task 0 完成（2026-09-02：只读核对 MySQL V1-V4、PGvector 13 条向量和 25 个基线测试）
- [x] Task 0 独立提交：`docs: record agentRag baseline`

## Task 1：定义 Agent、RAG 和模型适配契约

**目标：** 先稳定模块边界，避免后续把 Agent 逻辑继续堆进 Controller 或把 RAG 结果写死在提示词里。

**预期文件：**

- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/RentalAgentRuntime.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/AgentContext.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/AgentStep.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/tool/ToolDefinition.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/tool/ToolRegistry.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/config/ai/AiAgentProperties.java`
- Modify: `web/web-app/src/main/resources/application-default.yml`
- Modify: `web/web-app/src/main/resources/application-docker.yml`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/agent/AgentContractTest.java`

**接口要求：**

- `RentalAgentRuntime.execute(AgentContext, Consumer<ChatSseEvent>)`。
- 工具定义必须包含 name、description、permission、timeout 和参数 schema。
- `AgentContext` 必须绑定 userId、conversationId、message、history、goal、step budget。
- 模型配置支持 `primaryModel`、`fallbackModel`、超时、最大步数和是否记录轨迹。

**实施步骤：**

1. 用 record/DTO 固化上下文、工具结果、轨迹和错误分类。
2. 把现有 `RentalChatEngine` 的公共行为映射到新 Runtime，保留旧 SSE 外部协议。
3. 增加 Luna-first 的配置项，但不在 Java 代码里写死供应商 Key。
4. 对未知工具、越权工具、超步数和超时定义可测试的拒绝结果。

**自动验证：**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AgentContractTest test
```

**手工验证：**

- 配置模型名即可切换 primary，不改业务代码。
- 删除模型 Key 不会导致 Spring 应用启动失败。

**风险和停止条件：** 如果 Spring AI 1.0.0 的当前 ChatClient API 无法表达所需工具循环，保留现有 ChatClient 封装，在 Runtime 外包一层适配器，不能强行升级 Spring Boot/Spring AI 大版本。

- [x] Task 1 完成（2026-09-02：Agent 上下文、结果、轨迹、权限、运行时接口和配置契约，2 个契约测试通过）
- [x] Task 1 独立提交：`feat: define rental agent contracts`

## Task 2：补齐 RAG 文档元数据和结构化切片

**目标：** 让知识片段能被追溯、过滤和解释，且不影响已经存在的 Admin 上传和索引能力。

**预期文件：**

- Modify: `web/web-admin/src/main/java/com/atguigu/lease/web/admin/service/ai/impl/DocumentKnowledgeServiceImpl.java`
- Modify: `web/web-admin/src/main/java/com/atguigu/lease/web/admin/service/ai/impl/KnowledgeManagementServiceImpl.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/**`
- Modify: `web/web-app/src/main/resources/ai/rag-knowledge.md`
- Possibly create: `web/web-app/src/main/resources/db/migration/V5__ai_knowledge_metadata.sql`
- Test: `web/web-admin/src/test/java/com/atguigu/lease/web/admin/service/ai/**`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/rag/**`

**数据库规则：**

- Task 0 证明现有 `ai_knowledge_doc` 已有字段足够时，不创建 V5。
- 如果缺少版本、分类或 checksum，只能新增 nullable 字段和索引；禁止重建 `ai_knowledge_doc`。
- 现有数据默认映射到 namespace `default`，不能删除历史向量。

**实施步骤：**

1. 定义统一的 chunk metadata key。
2. 清洗标题、空行和重复内容。
3. 优先按 Markdown 标题切分，再对过长段落做递归字符切分。
4. 为每个 chunk 写入 document、chapter、section、category、source、version 和 checksum。
5. 保持已有 embedding 维度配置；维度变化必须作为单独的高风险变更，不纳入本 Demo。
6. 让索引过程可重复执行，避免重复向量。

**自动验证：**

```powershell
.\mvnw.cmd -pl web/web-app,web/web-admin -am -Dtest=*Knowledge*Test test
```

**手工验证：**

- 同一个文档重复索引不会产生重复 chunk。
- 可以根据 `category=DEPOSIT` 找到押金片段。
- 现有 Admin 上传功能仍可用。

**停止条件：** 本地现有知识表字段与 Mapper 不一致时，先回到 Task 0 更新基线，不直接写迁移。

- [x] Task 2 完成（2026-09-02：结构化标题切片、稳定 chunkId、分类/章节/checksum metadata 和房源 metadata，4 个测试通过；无需 MySQL 迁移）
- [x] Task 2 独立提交：`feat: add traceable rental knowledge chunks`

## Task 3：实现混合检索和引用

**目标：** 让 Agent 使用一个统一的知识工具，正常路径走向量 + 关键词混合召回，基础设施不可用时仍有本地 fallback。

**预期文件：**

- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/rag/RentalKnowledgeService.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/rag/HybridRentalKnowledgeService.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/rag/KnowledgeCitation.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/tools/**`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/ChatSseEvent.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/rag/**`

**实施步骤：**

1. 对原始问题做轻量 query rewrite：抽取“押金、付款、看房、维修、退租”等意图词。
2. 向量库召回 topK，关键词索引召回 topK。
3. 通过 chunkId/checksum 去重，按 namespace、版本和相似度阈值过滤。
4. 使用可解释的简单 rerank：向量分数、关键词覆盖、分类匹配加权。
5. 返回统一引用 DTO：标题、章节、来源、片段、分数、版本。
6. 在向量服务失败时切换本地 Markdown 关键词搜索，并标记 fallback 原因。

**自动验证：**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=*Knowledge*Test,*Citation*Test test
```

准备至少 10 条问题：押金、付款、看房、维修、退租各 2 条，验证命中和引用。

**手工验证：**

- 调用 AI 问押金问题，前端能看到章节和来源。
- 删除/停用向量配置后仍能看到本地知识引用。
- 房源价格不从 citation 中读取。

- [x] Task 3 完成（2026-09-02：查询改写、向量/关键词合并、去重、可解释 rerank、统一引用和本地降级，3 个测试通过）
- [x] Task 3 独立提交：`feat: add hybrid rental knowledge retrieval`

## Task 4：实现 Agent Loop、工具注册、权限和轨迹

**目标：** 把当前“模型聊天”升级为面试可讲的受控 Agent Runtime。

**预期文件：**

- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/DefaultRentalAgentRuntime.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/AgentLoop.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/PermissionPolicy.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/ToolHooks.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/TrajectoryRecorder.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/tools/RoomSearchTool.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/tools/RentalKnowledgeTool.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/tools/RoomDetailTool.java`
- Create/Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/tools/MoveInCostTool.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/agent/**`

**实施步骤：**

1. 注册四个只读工具和一个预约草稿工具。
2. 实现模型返回文本或 tool call 时的循环。
3. 每次工具调用经过 permission、参数、超时和步骤上限检查。
4. 工具结果进入 observation，再交给模型组织答案。
5. 记录脱敏轨迹，并通过 `trajectory` SSE 事件可选返回。
6. 兼容现有 `RentalChatServiceImpl` 和 fallback engine，避免前端协议断裂。
7. 当模型调用不可用或返回格式不可解析时，转到 fallback，且只能发送一个最终 fallback 结果。

**自动验证：**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=*Agent*Test,*Tool*Test,*RentalChat*Test test
```

必须覆盖：工具选择、未知工具拒绝、越权写工具拒绝、最大步数、超时、模型异常 fallback、用户隔离历史。

**手工验证：**

- 混合问题至少产生房源工具和知识工具结果。
- 轨迹不出现完整手机号、Key 或完整 prompt。
- Agent 不能直接产生预约确认结果。

- [x] Task 4 完成（2026-09-02：显式 Tool Calling 循环、5 个领域工具、白名单与权限、超时/步数/重复调用限制、结构化观察及 SSE 轨迹；15 个 Task 4 测试通过）
- [x] Task 4 独立提交：`feat: add rental agent harness`

## Task 5：接入现有预约和 Outbox 闭环

**目标：** 保留当前预约实现，在 Agent 输出和确定性预约服务之间建立明确边界，并验证并发幂等。

**预期文件：**

- Inspect/Modify only when needed: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/appointment/**`
- Inspect/Modify only when needed: `web/web-app/src/main/java/com/atguigu/lease/web/app/controller/ai/**`
- Inspect/Modify only when needed: `common/src/main/java/com/atguigu/lease/outbox/**`
- Inspect/Modify only when needed: `web/web-app/src/main/resources/db/migration/V5+__*.sql`
- Test: 预约草稿、确认、幂等、Outbox 集成测试

**实施步骤：**

1. Agent 只输出 `suggested_action` 或调用草稿服务，不开放确认工具。
2. 草稿重新校验 roomId、可租状态、时间和联系人。
3. Redis 草稿和令牌绑定 userId，TTL 10 分钟。
4. 确认阶段原子消费令牌，锁定/复核房源状态。
5. 一个 MySQL 事务写预约、幂等记录和 Outbox。
6. 重放和并发确认返回同一个 appointmentId。
7. RabbitMQ 不可用时保留 `PENDING`，恢复后由 publisher 重试。

**数据库检查：**

- 先用 Task 0 报告确认 `view_appointment.room_id`、幂等表和 Outbox 是否已存在。
- 已存在则不得再次 `CREATE TABLE` 或重复 `ALTER TABLE`。
- 需要新字段时使用下一 Flyway 版本，并提供迁移前后行数检查。

**自动验证：**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=*Appointment*Test,*Outbox*Test test
```

集成测试覆盖：未确认无预约、过期令牌、越权令牌、重复确认、并发确认、房源下架、Outbox 重试。

**手工验证：**

- 通过 API 创建草稿后查询数据库，预约数量不增加。
- 通过确认 API 后调用预约查询接口，能看到一条新记录。
- RabbitMQ 不可用时预约仍存在，Outbox 状态不是丢失。

- [ ] Task 5 完成
- [ ] Task 5 独立提交：`feat: harden rental appointment flow`

## Task 6：完成后端 API/SSE 闭环验收

**目标：** 不依赖 H5 页面，通过后端接口和 SSE 测试客户端从登录开始跑到我的预约，证明后端自身已经形成完整闭环。

**预期文件：**

- Create/Modify: `scripts/smoke-rental-agent.ps1`
- Create/Modify: `web/web-app/src/test/java/com/atguigu/lease/e2e/RentalAgentClosedLoopIT.java`
- Create/Modify: `web/web-app/src/test/java/com/atguigu/lease/web/app/controller/ai/AiApiContractTest.java`
- Modify: `docs/ai-rental-agent/api.md`
- Constraint: do not modify H5 source; verify the API contract remains consumable by the existing client

**实施步骤：**

1. 用测试客户端调用现有 Demo 登录接口获取 access-token。
2. 读取 `POST /app/ai/chat` SSE，解析 meta/message/recommendations/citations/done/error。
3. 断言 `MODEL`/`FALLBACK`、真实房源、RAG 引用和建议动作。
4. 调用预约草稿接口，并查询数据库证明此时预约数量不变。
5. 调用预约确认接口，断言只产生一条预约、幂等记录和 Outbox。
6. 重放确认令牌，断言返回相同 appointmentId。
7. 调用“我的预约”接口，断言能查到刚创建的预约。
8. 增加断网、模型超时、重复请求、空结果和越权请求的 API 断言。

**自动验证：**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=RentalAgentClosedLoopIT,AiApiContractTest test
.\scripts\smoke-rental-agent.ps1
```

**手工验证：**

- SSE 客户端可解析所有稳定事件，且不会依赖模型原始 JSON。
- 混合问题可得到房源、引用和 fallback 标识。
- API 流程必须把草稿和确认分成两个请求，未确认前不落库。

- [ ] Task 6 完成
- [ ] Task 6 独立提交：`test: verify backend rental agent loop`

## Task 7：RAG 评测、面试文档和简历素材

**目标：** 将实现变成可重复演示、可证明、可面试表达的交付物。

**预期文件：**

- Create/Modify: `docs/ai-rental-agent/agent-harness-explained.md`
- Create/Modify: `docs/ai-rental-agent/rag-evaluation.md`
- Modify: `docs/ai-rental-agent/api.md`
- Modify: `docs/ai-rental-agent/test-report.md`
- Modify: `readme.md`

**后端闭环脚本必须验证：**

1. Demo 登录接口。
2. fallback 混合问题。
3. 房源和引用非空。
4. 草稿后预约数量不变。
5. 确认后预约数量增加 1。
6. 重放令牌返回相同 ID。
7. 我的预约 API 能查询到该记录。
8. Outbox 有且只有一条对应事件。

**RAG 评测：**

- 记录问题、期望分类、topK、命中文档、MRR/HitRate 和人工 faithfulness 判断。
- 不把一次模型回答当成 RAG 质量证明。

**验证命令：**

```powershell
git branch --show-current
.\mvnw.cmd -pl web/web-app -Dtest=RentalAgentClosedLoopIT test
.\scripts\smoke-rental-agent.ps1
```

Docker、Testcontainers 和真实 Luna smoke test 只有在用户允许启动对应依赖、并且本轮确实执行成功后，才能标记通过。H5 构建和 Playwright 不属于本阶段验证。

- [ ] Task 7 完成
- [ ] Task 7 独立提交：`docs: document backend rental agent demo`

## Task 8：最终审计和交付门禁

**目标：** 确认交付没有破坏原项目，也没有把 Demo 说成生产系统。

**审计清单：**

- [ ] 当前分支仍为 `agentRag`。
- [ ] `master` 未被修改。
- [ ] `.idea/misc.xml` 未暂存、未提交。
- [ ] 没有真实 API Key、手机号批量数据或本地密码进入 Git。
- [ ] 没有删除或重建现有业务表。
- [ ] 每个数据库变化都有递增 Flyway 版本和测试记录。
- [ ] fallback 和 model 两条路径都能解释。
- [ ] Agent 工具权限、最大步骤和超时有测试。
- [ ] 预约未确认不落库，确认幂等，Outbox 同事务。
- [ ] RAG 引用可追溯，房源真值来自 MySQL。
- [ ] 文档命令与仓库实际路径一致。
- [ ] 没有未结束的测试进程。

**最终命令：**

```powershell
git status --short --branch
git diff --check
rg -n "api-key:\s*[^$<]|AI_(CHAT|EMBED)_API_KEY=.+|access-key-secret:\s*[^$<]" . -g '!target/**' -g '!node_modules/**' -g '!.env'
```

只有审计清单全部满足，才可以在简历中写“完成 Agent + RAG 租房闭环 Demo”。

- [ ] Task 8 完成
- [ ] 最终交付提交：`docs: finalize rental agent demo verification`

## 5. 计划完成定义

本计划不是“代码写完”就结束，而是以下证据全部存在：

1. 规格中的两条路径都能运行，至少 fallback 路径不依赖外部模型。
2. Agent Loop、工具权限、RAG 引用和预约确认边界都有代码和测试。
3. 当前本地数据库没有被破坏，迁移是增量且可追踪的。
4. API/SSE 脚本能重复验证完整后端流程；H5 可在后续直接消费稳定契约。
5. 面试解释文档能让开发者不打开代码也能讲清楚完整链路。
6. 所有未执行的 Docker、真实模型和浏览器验证都明确标注为待执行，不虚报通过。

## 6. 批准门

在用户明确批准本文档和对应 spec 之前：

- 不进入 Task 0 之后的代码实现。
- 不新建或修改数据库迁移。
- 不复制或修改前端源码；H5 集成延期到后续阶段。
- 不启动 Docker。

用户批准后按 Task 0 -> Task 8 顺序执行；每完成一个 Task 停在独立验收点，报告变更、测试输出、数据库影响和剩余风险，再进入下一个 Task。
