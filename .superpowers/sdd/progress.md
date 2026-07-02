# Plan 2 (AI RAG) — Progress Ledger

Plan: docs/superpowers/plans/2026-06-30-ai-rag-feature.md
Spec: docs/superpowers/specs/2026-06-30-ai-rental-agent-rag-design.md
Branch: agentRag
Execution mode: direct in-controller (无运行环境;仅编译 + 单测;运行验证留用户)
Build env prefix (每个 mvn/java 调用都要带):
  export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"

## 关键技术决策(实现时已确认/修正)
- PgVector 双数据源:定义第二个 DataSource 会让 Boot 不再自动建主源,故 PgVectorDataSourceConfiguration **显式定义 MySQL 主源(@Primary)+ pg 源**,整体 @ConditionalOnProperty("app.datasource.pg.url")。
- AiKnowledgeDoc extends BaseEntity(自带 id/createTime/updateTime/isDeleted)。
- RoomInfo 字段:roomNumber / rent / apartmentId / isRelease(ReleaseStatus 枚举,RELEASED=1)。
- ApartmentInfo 字段:name / provinceName / cityName / districtName / addressDetail。
- v1 用**手动检索**(RentalChatService 里 vectorStore.similaritySearch),不用 QuestionAnswerAdvisor。
- **T5/T6 修正**:roomRef 在 metadata 里存 Long,过滤值也必须传 Long(原计划写 String.valueOf 会导致过滤失效)。T8 的 docId 同理。
- **T6 修正**:删房间后 getById 返回 null,syncRoom 会提前 return 不清理向量。故 RoomChangedEvent 带 Action(SAVE/DELETE);监听对 DELETE 调用新增的 `deleteRoomVectors(roomId)`。
- **T7 修正**:TokenTextSplitter 单测不能用 `"A".repeat(N)`(BPE 会把重复单字符合并成极少 token,触发不了分片),改用大量**不同**词。
- **T9 修正**:RoomInfo 无"卧室数/户型"字段,计划里的 `roomCount` 参数无法落地,已从 RoomSearchTool 移除以免误导 LLM;只保留 city/district(经 ApartmentInfo 过滤)/maxMonthlyRent。
- **T14 修正**:计划模板里 `chunk-overlap` 不是 RagProperties 字段,已对齐为 min-chunk-size-chars / min-chunk-length-to-embed / max-num-chunks;模板用 `<占位符>` 风格与原文件一致。

## 进度
- [x] T1  Spring AI 依赖(BOM+openai/pgvector/tika+postgres+webflux) — commit a179584
- [x] T2  RagProperties + AiRedisConstant — commit 48520d2
- [x] T3  PgVector 第二数据源 + PgVectorStore(条件装配,修正双数据源主源) — commit 48520d2
- [x] T4  AiKnowledgeDoc 实体 + Mapper + DDL — commit e35dac0
- [x] T5  RoomKnowledgeService(reindexAll/syncRoom/deleteRoomVectors)+ 单测 2 passed — commit 0a223ae
- [x] T6  房源变更事件 + 异步监听(@EnableAsync;事件带 Action)— commit 913d94d
- [x] T7  DocumentKnowledgeService 文档管线 + 单测 1 passed — commit 1e68c2f
- [x] T8  知识库管理接口(上传/分页/删除/重建索引) — commit ec9c197
- [x] T9  RoomSearchTool @Tool(去掉 roomCount;city/district 经 ApartmentInfo 过滤) — commit 42d627b
- [x] T10 ChatClient 配置(系统提示 + RoomSearchTool) — commit 42d627b
- [x] T11 对话 VO(ChatRequestVo/RoomCitationVo/ChatSseEvent) — commit 42d627b
- [x] T12 SSE 对话服务 + 控制器 + 单测 3 passed — commit 42d627b
- [x] T13 鉴权:`/app/ai/chat` 在 `/app/**` 拦截内,需登录(符合预期,无代码改动)— 见 commit d2de381 说明
- [x] T14 yml 模板补 AI 段(两个 application-template.yml;字段对齐 RagProperties) — commit d2de381
- [x] T15 验证(自动化部分,2026-07-01):
  - `mvn clean compile` 全模块 BUILD SUCCESS(仅 Lombok 对老 VO 的无害 @EqualsAndHashCode 警告,非 AI 改动)。
  - 三单测一次性合并复测全绿(DocumentKnowledge 1 / RoomKnowledge 2 / RentalChat 3)。
    命令:`mvn test -Dtest='RoomKnowledgeServiceImplTest,DocumentKnowledgeServiceImplTest,RentalChatServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false -pl web/web-admin,web/web-app -am`
- [ ] T15 运行期验证(进行中,2026-07-01~02):
  - **环境已全部就绪**:MySQL8.0.41 / Redis / RabbitMQ / MinIO(9000)/ PostgreSQL16+pgvector 全部本机起好,各端口验证通过;pgvector 扩展已建。本机 JDK=`/e/JDK/JDK21`,Maven=`/e/Maven/apache-maven-3.8.6`。
  - **web-admin 首次真实启动成功**(2026-07-02):MySQL / pg-vector-pool / RabbitMQ 全连上,`vector_store` 表自动建好。运行期暴露并修复 3 处问题:
    1. **PgVectorDataSourceConfiguration 真 bug**:`pgJdbcTemplate(DataSource pgDataSource)` 因主源 `@Primary` 被注入了 MySQL 而非 PG,导致 `CREATE EXTENSION` 发到 MySQL。修复:参数加 `@Qualifier("pgDataSource")`。
    2. `spring.autoconfigure.exclude` 类名漏 `.autoconfigure` 子包 → 排除不生效,与手写 vectorStore bean 冲突。已改对(两份 yml + 两份模板)。
    3. `mybatis-plus.configuration.log-impl` 漏 `.logging`(我写 yml 时笔误)。已改对(模板本来是对的)。
  - 待验证:`/admin/ai/docs` 上传→INDEXED(证明 GLM embedding 通)、房源 reindex、web-app SSE 对话。admin 登录走验证码(Redis 明文 key=`admin:login:<uuid>`),可用 admin/MD5(123456) 脚本化登录。
  - 起 Postgres+pgvector,填 PG_URL/USER/PASSWORD + AI_*。
  - `mvn -pl web/web-admin spring-boot:run` → 上传文档 → 确认 ai_knowledge_doc.status=INDEXED。
  - 触发房源 reindex → 确认 vector_store 有 namespace=rooms 行。
  - `mvn -pl web/web-app spring-boot:run` → curl SSE /app/ai/chat(带 access-token)→ 确认流式 + done+引用。

## 环境路径(本机,2026-07-01)
- JDK21:`/e/JDK/JDK21`(java 21.0.9 LTS)
- Maven:`/e/Maven/apache-maven-3.8.6`
- 旧的 `/d/SoftWare/...` 路径是另一台机器,本机用 `/e/...`。
- 每次 mvn/java 调用前缀:`export JAVA_HOME="/e/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/e/Maven/apache-maven-3.8.6/bin:$PATH"`

## 恢复指引(新会话/重启后)
- 读本文件 + Plan 2 文档(`docs/superpowers/plans/2026-06-30-ai-rag-feature.md`)即可接上。
- 已推送到 `origin/agentRag`,换机 `git pull` 即可。
- 代码侧 T1–T14 全部完成,编译+单测已验证;**剩下全是运行期验证**(需用户起 Postgres+pgvector / MinIO / GLM key)。
