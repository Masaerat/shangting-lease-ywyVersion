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
- [ ] T15 验证:`mvn clean compile` 全模块已绿(exit 0);三单测各跑过(RoomKnowledge 2 / DocumentKnowledge 1 / RentalChat 3)。**待办**:一次性合并复测 + `mvn clean package -DskipTests`(可选)。运行期验证(GLM key + pgvector)留用户。

## 恢复指引(新会话/重启后)
- 读本文件 + Plan 2 文档(`docs/superpowers/plans/2026-06-30-ai-rag-feature.md`)即可接上。
- 已推送到 `origin/agentRag`,换机 `git pull` 即可。
- 下一步:T15 收尾(可选的一次性复测);之后全是运行期验证,需要 Postgres+pgvector / MinIO / GLM key,见计划 T15 的 operator steps。
