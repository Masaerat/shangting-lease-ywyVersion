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
- **T5/T6 修正**:roomRef 在 metadata 里存 Long,过滤值也必须传 Long(原计划写 String.valueOf 会导致过滤失效)。
- **T6 修正**:删房间后 getById 返回 null,syncRoom 会提前 return 不清理向量。故 RoomChangedEvent 带 Action(SAVE/DELETE);监听对 DELETE 调用新增的 `deleteRoomVectors(roomId)`。

## 进度
- [x] T1  Spring AI 依赖(BOM+openai/pgvector/tika+postgres+webflux) — commit a179584
- [x] T2  RagProperties + AiRedisConstant — 在 commit 48520d2
- [x] T3  PgVector 第二数据源 + PgVectorStore(条件装配,修正了双数据源主源问题) — commit 48520d2
- [x] T4  AiKnowledgeDoc 实体 + Mapper + DDL — commit e35dac0
- [x] T5  RoomKnowledgeService(reindexAll/syncRoom)+ 单测 2 passed — commit 0a223ae
- [x] T6  房源变更事件 + 异步监听(@EnableAsync;deleteRoomVectors;事件带 Action)— commit 913d94d
- [x] T7  DocumentKnowledgeService 文档管线 + 单测(测试数据改为不同词,避免 BPE 合并)— commit 1e68c2f
- [x] T8  知识库管理接口(上传/分页/删除/重建索引;docId 过滤用 Long;reindexNamespace 补全文档重灌)— 见最新 commit
- [ ] T9  RoomSearchTool @Tool(isRelease 用 ReleaseStatus.RELEASED;city/district 经 ApartmentInfo 过滤)
- [ ] T10 ChatClient 配置
- [ ] T11 对话 VO
- [ ] T12 SSE 对话服务 + 控制器(含单测)
- [ ] T13 鉴权说明(/app/ai/chat 在拦截器内,需登录,符合预期)
- [ ] T14 yml 模板补 AI 段(两个 application-template.yml)
- [ ] T15 验证(mvn clean compile + 单测;运行留用户)

## 恢复指引(新会话/重启后)
- 读本文件 + Plan 2 文档即可接上。
- 下一步:从 T7 继续(DocumentKnowledgeService 文档管线)。
