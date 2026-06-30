# AI 选房对话助手 + 企业级 RAG — 设计文档

- **日期**: 2026-06-30
- **分支**: `agentRag`
- **模块**: `web-app`(C 端对话)+ `web-admin`(后台知识库管理)+ `common`(配置/工具)
- **状态**: 待评审

---

## 1. Context(背景与目标)

项目 `shangting-lease` 是一个 Spring Boot 多模块租房/ lease 管理系统(MySQL + MyBatis-Plus + Redis + RabbitMQ + MinIO)。此前在已删除的 `feature/ai-rental-agent-rag` 分支上做过一版 AI 选房助手(玩具级,内存检索)。现在要在干净的 `agentRag` 分支上**重新开发一个企业级、可写进简历的 RAG 系统**。

**核心诉求**
- 自然语言对话找房,流式(SSE)返回回答 + 推荐房源 + 引用溯源。
- Hybrid 检索:**语义召回(RAG over 房源 + 上传文档)** + **结构化过滤(Function Calling 工具查 DB)**。
- 企业级知识库管理:文档上传 → 多格式解析 → 可配置分片 → 向量化 → pgvector,带元数据与引用溯源。
- 技术栈整体现代化:升级 **JDK 21 + Spring Boot 3.4.x + Spring AI 1.0 GA**,**强约束:不破坏现有老代码(零回归)**。
- 配置全抽到 yml,模型名 / API key / 维度等留 `${...}` 占位符由用户自行填写。

**预期产出**:一套可演示、可维护、参数化的企业级 RAG,能在简历中体现「Spring AI、向量检索、Function Calling、SSE 流式、虚拟线程、多数据源、文档处理管线」等能力。

---

## 2. 范围(Scope)

**In scope(本期)**
- JDK 17 → 21、Spring Boot 3.0.5 → 3.4.x 全量升级 + 零回归验证。
- 接入 Spring AI 1.0 GA(OpenAI 兼容端点接 GLM:chat / embedding / tool-calling)。
- 新增 PostgreSQL + pgvector 向量库(独立第二数据源)。
- 房源数据自动同步 → 向量。
- 文档知识库管理:上传(MinIO)、多格式解析(Tika)、可配置分片(TokenTextSplitter)、入库、删除、重建索引。
- C 端对话接口(SSE,多轮历史存 Redis),Hybrid 检索 + 工具调用 + 引用。
- 后台知识库管理接口(放在 `web-admin`)。

**Out of scope(本期不做)**
- 语音 / 多模态 / 图像理解。
- 管理后台前端页面(只提供后端接口;前端另做)。
- 长期跨会话用户记忆画像(只做会话内最近 N 轮)。
- 模型微调 / 自训练 embedding。
- 多租户隔离(用 `namespace` 做逻辑分区即可)。

---

## 3. 技术栈与版本升级(最高风险区)

| 项 | 现状 | 目标 | 兼容性说明 |
|---|---|---|---|
| JDK | 17 | **21 (LTS)** | 编译目标改 21;启用虚拟线程 |
| Spring Boot | 3.0.5 | **3.4.x** | Spring AI 1.0 GA 支持上限为 3.4(再高需升 Spring AI 2.0 + Boot 4) |
| Spring AI | — | **1.0.0 GA(BOM)** | 新增 |
| MyBatis-Plus | 3.5.3.1 | 视情况升 3.5.7+ | 与 Boot 3.4 对齐,若冲突再升 |
| knife4j | 4.1.0 | 视情况升 4.5.0 | 4.5+ 明确支持 Boot 3.4 |
| spring-rabbit | 3.1.2 | 由 Boot 3.4 托管(3.2.x) | 无需手填版本 |
| minio / jjwt / aliyun-sms | 现状 | 不变 | 与 Boot 版本解耦 |

**新增依赖**
- `spring-ai-bom`(1.0.0,放在根 `<dependencyManagement>`)
- `spring-ai-starter-model-openai`
- `spring-ai-starter-vector-store-pgvector`
- `spring-ai-tika-document-reader`(多格式文档解析)
- `org.postgresql:postgresql`(Postgres 驱动)

**强约束 — 向后兼容(零回归)**
升级必须保证现有 `web-admin` / `web-app` / `common` / `model` 全部模块:**编译通过 → 应用启动 → 既有接口行为不变**。这通过 §12 的回归验证清单强制保障。任何因升级产生的破坏性改动(如废弃 API)必须就地修复,不引入功能变更。

**虚拟线程**:`spring.threads.virtual.enabled=true`,SSE 长连接 / IO 密集的 AI 调用受益。

---

## 4. 架构总览

```
                ┌─────────────────────────── web-app (C 端) ───────────────────────────┐
   用户 ──SSE──▶ AiChatController                                                       │
                     │                                                                  │
                     ▼                                                                  │
              RentalChatService                                                         │
              (取 Redis 多轮历史 → 组装 Prompt)                                          │
                     │                                                                  │
                     ▼                                                                  │
          Spring AI ChatClient                                                          │
          ┌───────────┴────────────┐                                                    │
          ▼                        ▼                                                    │
  QuestionAnswerAdvisor       @Tool: RoomSearchTool                                      │
  (语义召回 top-k)            (硬过滤:价格/位置/户型)                                    │
          │                        │                                                    │
          ▼                        ▼                                                    │
   PgVectorStore             RoomInfoService / ApartmentInfoService                      │
   (Postgres)                (MySQL via MyBatis-Plus)                                    │
          │                                                                     │       │
          ▼                                                                     │       │
      GLM embedding + chat (OpenAI 兼容端点)                                     │       │
                                                                                │       │
                ┌───────────────────── web-admin (后台) ─────────────────────┐        │       │
   管理员 ──HTTP─▶ KnowledgeController                                          │ ◀──────┘
                     │                                                          │
                     ▼                                                          │
          KnowledgeManagementService                                           │
                     │                                                          │
   ┌─────────────────┼──────────────────────┐                                  │
   ▼                 ▼                      ▼                                  │
 上传 MinIO   DocumentKnowledgeService   RoomKnowledgeService                 │
 (对象存储)   (Tika 解析→分片→向量化)     (房源→向量化,自动同步)              │
                     │                      │                                  │
                     └──────────┬───────────┘                                  │
                                ▼                                              │
                          PgVectorStore (Postgres)                             │
                                ▲                                              │
                                │ 元数据                                       │
                          MySQL: ai_knowledge_doc                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**关键边界**
- **MySQL**:现有业务数据 + 新增 `ai_knowledge_doc` 文档元数据表(MyBatis-Plus 管)。
- **Postgres + pgvector**:Spring AI `PgVectorStore` 自管 `vector_store` 表,存放所有分片向量(房源 + 文档),通过 `metadata.namespace` 分区。
- **两套 DataSource 严格隔离**:MySQL 为 primary(MyBatis-Plus 用);Postgres 由独立 `DataSource` + `JdbcTemplate` + 手动构造的 `PgVectorStore` bean,**禁用** `PgVectorStoreAutoConfiguration`(避免它抢占 primary DataSource)。

---

## 5. 模块结构

### 5.1 web-app(C 端对话)

```
web-app/src/main/java/com/atguigu/lease/web/app/
├─ controller/ai/AiChatController.java        POST /app/ai/chat, 返回 SseEmitter
├─ service/ai/
│   └─ RentalChatService.java + Impl          SSE 编排:历史 → ChatClient.stream → 事件
├─ tools/RoomSearchTool.java                  Spring AI @Tool,硬过滤,走 MyBatis-Plus
├─ config/ai/AiClientConfiguration.java       ChatModel/EmbeddingModel/ChatClient/Advisor
└─ vo/ai/
    ├─ ChatRequestVo.java                     {message, conversationId}
    ├─ ChatSseEvent.java                      {type: message|tool|done|error, payload}
    └─ RoomCitationVo.java                    {roomId, apartmentName, roomName, monthlyRent, source}
```

> 注:web-app 只**消费**向量库(对话时经 advisor 读取 pgvector),不负责入库。所有知识写入(房源同步 + 文档上传)都在 web-admin。

### 5.2 web-admin(后台知识库管理)

```
web-admin/src/main/java/com/atguigu/lease/web/admin/
├─ controller/ai/KnowledgeController.java     /admin/ai/docs: 上传/列表/详情/删除/重建
├─ service/ai/
│   ├─ RoomKnowledgeService.java + Impl       房源 → Document → 向量化 + 增量/全量同步(监听房间变更事件)
│   ├─ DocumentKnowledgeService.java + Impl   文档管线:存 MinIO → 解析 → 分片 → 向量化
│   └─ KnowledgeManagementService.java + Impl 文档 CRUD / 按 docId 删向量 / 按 namespace 重建
└─ vo/ai/
    ├─ KnowledgeDocVo.java                    doc 元信息
    ├─ KnowledgeDocQueryVo.java               列表查询条件(namespace/status/keyword)
    └─ UploadResultVo.java                    {docId, status, chunkCount}
```

### 5.3 common(共享配置/常量)

```
common/src/main/java/com/atguigu/lease/
├─ config/ai/
│   ├─ PgVectorDataSourceConfiguration.java   第二数据源 + JdbcTemplate + PgVectorStore bean
│   └─ RagProperties.java                     @ConfigurationProperties("app.ai.rag")
└─ constant/
    └─ AiRedisConstant.java                   会话历史 key 前缀 / TTL
```

> 约定(沿用现有):`@RestController` 返回 `Result<T>`(非流式);SSE 端点返回 `SseEmitter`;`@Autowired` 字段注入;VO `@Data`+`@Schema`;实体放 `model` 模块;配置类放 `common` 或模块内 `config`。

---

## 6. 数据模型

### 6.1 MySQL:`ai_knowledge_doc`(文档元数据,MyBatis-Plus)

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| doc_name | varchar(255) | 原始文件名 |
| content_type | varchar(128) | MIME(pdf/docx/md/txt) |
| size_bytes | bigint | |
| namespace | varchar(64) | 逻辑分区,默认 `default` |
| status | varchar(32) | `UPLOADING` / `INDEXED` / `FAILED` |
| chunk_count | int | 分片数 |
| minio_object_key | varchar(255) | MinIO 对象 key |
| minio_bucket | varchar(128) | 桶名 |
| error_message | varchar(512) | 失败原因 |
| create_time / update_time | datetime | (BaseEntity 复用) |

实体放 `model` 模块,Mapper 放 `web-admin`。

### 6.2 Postgres:`vector_store`(Spring AI PgVectorStore 自管)

Spring AI 自动建表,字段大致:`id(uuid)`、`content`、`metadata(jsonb)`、`embedding(vector(dim))`。
- `metadata` 内统一放:`{docType: "room"|"doc", docId, source, page, namespace, roomRef?, ...}`,用于按文档删除、按 namespace 过滤、引用溯源。

---

## 7. 知识库管线(企业级 RAG 核心)

### 7.1 房源同步管线(自动)

- **触发**:`RoomInfoServiceImpl` 的 save/update/removeAfter 发布 Spring `ApplicationEvent`(那里已有缓存失效逻辑,顺势加事件)。
- **构造文档**:`RoomKnowledgeService` 把房间拼成文本:`公寓名 / 省-市-区 / 面积 / 朝向 / 标签 / 租期价格 / 配套 / 费用`,挂 `metadata{docType:"room", roomRef:roomId, namespace:"rooms"}`。
- **入库**:删该 roomRef 旧向量(metadata 过滤)→ 重新 embedding → 入库。
- **首次全量**:提供一个内部初始化接口/启动开关,把现有房间一次性灌入。

### 7.2 文档处理管线(手动上传,参数可配)

```
上传(multipart) 
  → 校验类型/大小
  → MinIO 存对象(复用现有 MinioConfiguration),ai_knowledge_doc 记 UPLOADING
  → 异步:
      TikaDocumentReader 解析(MinIO 流)
      → 文本清洗
      → TokenTextSplitter 分片(参数见 §9)
      → 每片挂 metadata{docType:"doc", docId, source:doc_name, page, namespace}
      → GLM embedding(批量)
      → PgVectorStore.add(List<Document>)
      → ai_knowledge_doc 记 INDEXED + chunk_count
  → 失败:记 FAILED + error_message
```

- **异步处理**:`@Async`(已 `@EnableAsync`)+ 虚拟线程,前端轮询状态。
- **多格式**:Tika 覆盖 pdf/doc/docx/md/txt 等;PDF 优先用 `PagePdfDocumentReader` 拿到 page 元数据以支持页码溯源。

### 7.3 删除 / 重建

- 删除文档:`KnowledgeManagementService` → 删 MinIO 对象 + `vectorStore.delete(FilterExpression` 按 `docId` `)` + 删 `ai_knowledge_doc` 记录。
- 按 namespace 重建:删该 namespace 全部向量 → 触发房源重灌 + 文档重解析。

---

## 8. 对话与 Hybrid 检索流程(SSE)

1. `POST /app/ai/chat`,`Accept: text/event-stream`,body `ChatRequestVo{message, conversationId?}`(可选)。
2. Controller 创建 `SseEmitter`(虚拟线程承载),返回。
3. `RentalChatService`:
   - `conversationId` 缺省则按 `userId` 生成;从 Redis(`CacheUtil`)取该会话最近 **10** 轮。
   - 组装 `Prompt`:system(租房顾问人设 + 输出规范:必须给引用) + history + 当前问题。
4. `ChatClient` 配置(`AiClientConfiguration`):
   - `.defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).searchExaminer(top-k, similarityThreshold).build())` —— 语义召回房源+文档分片作为上下文。
   - `.defaultTools(roomSearchTool)` —— 注册硬过滤工具。
   - `chatOptions`:temperature、max-tokens 等可配。
5. `chatClient.prompt(prompt).stream()`:
   - 模型若发起 `RoomSearchTool` 调用 → Spring AI 自动执行(MyBatis-Plus 查 MySQL)→ 结果回灌模型 → 每个 token 推一个 `message` SSE 事件。
6. 流结束 → 推 `done` 事件,`payload.recommendations` 为引用的房源列表(`RoomCitationVo`)。
7. 异常 → 推 `error` 事件(流式中途无法再返回 `Result` JSON);记录日志。

**Hybrid 协同**:模糊/描述性问题(“氛围感强的两居”)走向量召回;明确硬约束(“朝阳区 ≤3000”)由模型经 `RoomSearchTool` 精确查库;两者由模型在 agent 循环中自行选择。

### RoomSearchTool(@Tool)签名(草案)
```java
@Component
public class RoomSearchTool {
    record RoomHit(Long roomId, String apartment, String room, BigDecimal rent, String location) {}

    @Tool(description = "按城市/区/户型/最高月租金精确筛选在租房源,返回候选列表")
    public List<RoomHit> searchRooms(String city, String district,
                                     Integer roomCount, BigDecimal maxMonthlyRent) { ... }
}
```

---

## 9. 配置(application.yml,占位符待用户填写)

> 当前仓库未提交 `application.yml`;本期需新建。下列 AI 段所有模型/key/维度用 `${...}` 占位。

```yaml
spring:
  threads:
    virtual:
      enabled: true
  ai:
    openai:
      base-url: ${AI_BASE_URL}                       # 如 https://open.bigmodel.cn/api/paas/v4
      api-key: ${AI_API_KEY}                         # 占位
      chat:
        options:
          model: ${AI_CHAT_MODEL}                    # 占位,用户填实际模型名
          temperature: 0.3
      embedding:
        options:
          model: ${AI_EMBED_MODEL}                   # 占位
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: ${AI_EMBED_DIM}                  # 与 embedding 模型一致
        # auto-configuration 实际禁用,见 §4;此段供参数读取
app:
  datasource:
    pg:                                              # 第二数据源(Postgres)
      url: ${PG_URL}
      username: ${PG_USER}
      password: ${PG_PASSWORD}
  ai:
    rag:
      chunk-size: 800
      chunk-overlap: 200
      min-chunk-size: 350
      top-k: 5
      similarity-threshold: 0.75
      history-turns: 10
      namespace-default: default
    chat:
      system-prompt: "你是一名专业的租房顾问……(完整提示词写在配置,便于调优)"
```

---

## 10. 错误处理

| 场景 | 处理 |
|---|---|
| 入参校验失败(在开始流之前) | 标准 `Result.fail` + `GlobalExceptionHandler` |
| LLM 超时 / 限流 / 网络异常 | 流内 `error` 事件 + 后端日志 |
| 工具(`@Tool`)抛异常 | 捕获 → 以 tool error 消息回灌模型,不中断流 |
| 文档解析/向量化失败 | `ai_knowledge_doc.status=FAILED` + `error_message`,前端可见 |
| 双数据源配置缺失(如本地未配 PG) | `@ConditionalOnProperty(app.datasource.pg.url)` 使向量相关 bean 可选降级,保证主业务仍能启动 |

---

## 11. 测试策略(TDD,先写测试再实现)

**单元**
- `RoomSearchTool`:mock `RoomInfoService`/`ApartmentInfoService`,验证筛选条件拼接。
- `RoomKnowledgeService`:验证房间→文档文本拼装、增量同步(先删后插)。
- `DocumentKnowledgeService`:验证分片参数被正确传给 `TokenTextSplitter`;验证失败状态记录。
- `RentalChatService`:用 **fake/mock `ChatModel`** 模拟「先 tool-call → 返回房源 → 再生成最终答案」整条 agent 循环,断言 SSE 事件序列(`message`…`done`)。
- `KnowledgeManagementService`:验证按 `docId` 删除向量时构造的 `FilterExpression` 正确。

**集成**
- `PgVectorStore`:用 Testcontainers 起 Postgres+pgvector(或 fake VectorStore)跑存取。
- 升级回归:见 §12。

**端到(手动)**
- `curl -N` 打 SSE 端点对真实 GLM 验证流式 + 引用。
- 上传一份 PDF 走完整管线,确认状态流转 `UPLOADING→INDEXED` 并能被检索引用。

---

## 12. 向后兼容 / 升级回归验证(强约束)

升级分两阶段独立验证,**先证明零回归,再加 AI 功能**:

**阶段 0 — 纯升级(不加任何 AI 代码)**
1. JDK 17→21、Boot 3.0.5→3.4.x;修编译错误与废弃 API(就地修复,不改行为)。
2. 依赖对齐(MyBatis-Plus / knife4j / spring-rabbit 视情况升版本)。
3. 验证:
   - `mvn clean compile` 全模块通过。
   - 应用能启动(MySQL/Redis/Rabbit/MinIO 连接正常)。
   - **既有接口冒烟**:`web-admin` 与 `web-app` 各挑代表性接口(登录、公寓/房间增查、预约、图形验证码)行为不变。
   - 既有单元测试全绿。

**阶段 1 — AI 模块**
在已验证零回归的基线上,按 §5 增量引入 AI 模块与配置。

---

## 13. 简历可写点(交付亮点)

JDK 21 虚拟线程 · Spring Boot 3.4 + Spring AI 1.0 GA · Hybrid 检索(向量召回 + Function Calling 工具)· 企业级文档处理管线(Tika 解析 + 可配置 TokenTextSplitter 分片)· MySQL 业务 / Postgres+pgvector 向量 双数据源隔离 · MinIO 对象存储 + 向量库联动 · SSE 流式对话 + 多轮上下文(Redis)· 引用溯源 · 配置化 RAG 参数 · 零回归升级。

---

## 14. 分阶段交付(供后续 writing-plans 拆解)

1. **阶段 0**:版本升级 + 零回归验证。
2. **基础设施**:第二数据源 + `PgVectorStore` + Spring AI Client 配置 + 占位符 yml。
3. **房源同步管线**:RoomKnowledgeService + 事件 + 首次全量。
4. **文档处理管线**:上传/MinIO/Tika/分片/向量化 + `ai_knowledge_doc` + 后台管理接口(web-admin)。
5. **对话与 Hybrid 检索**:ChatClient + Advisor + RoomSearchTool + SSE + 多轮历史 + 引用。
6. **打磨**:错误处理、参数调优、文档与测试补全。

---

## 15. 待办 / 待用户确认的开放项

- [ ] `AI_BASE_URL / AI_API_KEY / AI_CHAT_MODEL / AI_EMBED_MODEL / AI_EMBED_DIM` 的具体值(用户自行填入)。
- [ ] 后台知识库接口的鉴权:`web-admin` 现有管理员鉴权机制复用(待实现时确认其拦截器范围)。
- [ ] 文档大小上限 / 允许的 MIME 白名单(建议默认 ≤ 20MB,pdf/docx/md/txt)。
