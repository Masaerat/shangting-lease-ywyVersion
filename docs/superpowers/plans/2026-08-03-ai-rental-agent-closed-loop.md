# 27公寓 AI 租房顾问闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一个可通过 Docker Compose 复现的 AI 租房顾问，使租客能够从 H5 自然语言找房、检索租房知识、生成预约草稿、明确确认并在“我的预约”中看到唯一预约记录。

**Architecture:** 保持现有单体模块边界，在 `web-app` 内通过 Spring AI `ChatClient` 接入 GLM，并将房源查询、知识检索和预约确认拆成受控服务。模型只调用只读工具；预约写入采用 Redis 一次性令牌、MySQL 幂等记录和 Transactional Outbox。没有模型 Key 时使用同 DTO 的规则降级 Agent。

**Tech Stack:** Java 21, Spring Boot 3.4.x, Spring AI 1.0.x, MyBatis-Plus, MySQL 8, Redis 7, RabbitMQ 3, PostgreSQL 16 + PGvector, MinIO, Flyway, Testcontainers, Vue 3, TypeScript, Vant, Vitest, Playwright, Docker Compose.

## Global Constraints

- Java 基线必须为 21；Spring Boot 必须保持在 3.4.x；Spring AI 必须保持在 1.0.x。
- 默认启动命令必须为 `docker compose up --build`，且空数据卷可完成整个演示流程。
- GLM Key 只能从 `.env` 或环境变量读取；`.env` 必须被 Git 忽略，Git 只提交 `.env.example`。
- 模型只能选择只读房源和知识工具；任何聊天文本都不能直接写预约。
- 预约必须通过 10 分钟 TTL、绑定用户、单次消费的确认令牌创建，并支持幂等重放。
- 预约和 Outbox 必须在同一 MySQL 事务落库；RabbitMQ 故障不能丢失预约事件。
- 未配置 GLM 时必须返回 `mode=FALLBACK`，且登录、找房、知识问答和预约闭环仍可运行。
- H5 源码纳入本仓库 `frontend/rent-house-h5`；原目录 `E:\frontend\rentHouseH5\rentHouseH5` 不修改。
- 不修改 Admin 前端，不自动签约或支付，不提交真实用户数据。
- 保留用户已有 `.idea/misc.xml` 修改；禁止使用 `git reset --hard`、`git checkout --` 或覆盖式回滚。
- 每个生产行为遵循 RED -> GREEN -> REFACTOR；每个任务只提交列出的相关文件。

---

### Task 1: 固定 Java 21 构建基线和可提交配置

**Files:**
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
- Create: `.env.example`
- Modify: `.gitignore`
- Modify: `pom.xml`
- Modify: `common/pom.xml`
- Modify: `web/pom.xml`
- Modify: `web/web-app/pom.xml`
- Modify: `web/web-app/src/main/resources/application.yml`
- Modify: `web/web-admin/src/main/resources/application.yml`
- Test: `web/web-app/src/test/java/com/atguigu/lease/BuildBaselineTest.java`

**Interfaces:**
- Produces: Java 21 Maven Wrapper build; `${MYSQL_*}`、`${REDIS_*}`、`${RABBITMQ_*}`、`${MINIO_*}`、`${GLM_*}`、`${PGVECTOR_*}` configuration contract.

- [ ] **Step 1: Record the current build failure**

Run:

```powershell
C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd -pl web/web-app -am -DskipTests compile
```

Expected: FAIL under JDK 21 with the existing Lombok/Javac `JCTree$JCImport.qualid` incompatibility.

- [ ] **Step 2: Add a baseline context test**

```java
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class BuildBaselineTest {
    @Test
    void applicationContextLoadsOnJava21() {
        assertThat(Runtime.version().feature()).isEqualTo(21);
    }
}
```

- [ ] **Step 3: Upgrade dependency management and add required starters**

Set `java.version=21`, Spring Boot `3.4.12`, Spring AI BOM `1.0.3`, and add Actuator, Validation, Flyway MySQL, PostgreSQL JDBC, Spring AI OpenAI starter, Retry, Testcontainers, Awaitility and Mockito test dependencies. Retain MyBatis-Plus and existing API dependencies.

- [ ] **Step 4: Generate Maven Wrapper and sanitize configuration**

Run:

```powershell
C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd wrapper:wrapper -Dmaven=3.9.9
```

Track `application.yml` files and replace every credential with environment-backed defaults such as:

```yaml
spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/lease?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true}
    username: ${MYSQL_USER:lease}
    password: ${MYSQL_PASSWORD:lease}
  ai:
    openai:
      base-url: ${GLM_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
      api-key: ${GLM_API_KEY:}
      chat.options.model: ${GLM_CHAT_MODEL:glm-4-flash}
management.endpoints.web.exposure.include: health,info
```

`.env.example` contains variable names and demo defaults but `GLM_API_KEY=` remains empty. Before replacing the ignored local config, preserve the user's local key only in ignored `.env` and never print it.

- [ ] **Step 5: Verify GREEN**

Run:

```powershell
.\mvnw.cmd -pl web/web-app -am -Dtest=BuildBaselineTest test
.\mvnw.cmd -pl web/web-app -am -DskipTests package
```

Expected: both commands exit 0 on Java 21.

- [ ] **Step 6: Commit**

```powershell
git add .mvn mvnw mvnw.cmd .env.example .gitignore pom.xml common/pom.xml web/pom.xml web/web-app/pom.xml web/web-app/src/main/resources/application.yml web/web-admin/src/main/resources/application.yml web/web-app/src/test/java/com/atguigu/lease/BuildBaselineTest.java
git commit -m "build: upgrade rental platform to Java 21"
```

### Task 2: 固化 MySQL 迁移、演示数据和 PGvector 结构

**Files:**
- Create: `web/web-app/src/main/resources/db/migration/V1__lease_baseline.sql`
- Create: `web/web-app/src/main/resources/db/migration/V2__ai_agent_tables.sql`
- Create: `web/web-app/src/main/resources/db/migration/V3__demo_seed.sql`
- Create: `db/pgvector/001-schema.sql`
- Create: `db/pgvector/002-keyword-index.sql`
- Modify: `db/ai-rental-agent/pgvector-schema.sql`
- Test: `web/web-app/src/test/java/com/atguigu/lease/migration/LeaseMigrationIT.java`

**Interfaces:**
- Produces: repeatable MySQL schema; demo user `13800000000`; six rooms; `ai_conversation`、`ai_message`、`ai_appointment_idempotency`、`appointment_event_outbox`; PGvector `ai_rental_knowledge_chunk`.

- [ ] **Step 1: Write the migration integration test**

```java
@Testcontainers(disabledWithoutDocker = true)
class LeaseMigrationIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("lease").withUsername("lease").withPassword("lease");

    @Test
    void emptyDatabaseMigratesAndContainsDemoInventory() {
        Flyway.configure().dataSource(mysql.getJdbcUrl(), "lease", "lease").load().migrate();
        assertThat(jdbc.queryForObject("select count(*) from room_info", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from user_info where phone='13800000000'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_event_outbox", Integer.class)).isZero();
    }
}
```

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=LeaseMigrationIT test
```

Expected: FAIL because migration resources and new tables do not exist.

- [ ] **Step 3: Capture and sanitize the existing schema**

Use the local MySQL binary to export DDL only:

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe' --protocol=tcp --host=localhost --user=root --password=123456 --no-data --skip-comments --skip-dump-date --result-file='web\web-app\src\main\resources\db\migration\V1__lease_baseline.sql' lease
```

Review the generated DDL and remove host-specific definers. `V3__demo_seed.sql` inserts only fabricated users, regions, apartments, rooms, labels, payment methods, images and one active lease that excludes one room from recommendations.

- [ ] **Step 4: Add new MySQL and PGvector tables**

`V2__ai_agent_tables.sql` includes uniqueness and state columns, including:

```sql
create table ai_appointment_idempotency (
  id bigint primary key auto_increment,
  user_id bigint not null,
  token_hash char(64) not null,
  appointment_id bigint not null,
  created_at datetime not null default current_timestamp,
  unique key uk_ai_appointment_token (user_id, token_hash)
);

create table appointment_event_outbox (
  id bigint primary key auto_increment,
  aggregate_id bigint not null,
  event_type varchar(64) not null,
  payload_json json not null,
  status varchar(16) not null default 'PENDING',
  attempts int not null default 0,
  next_attempt_at datetime not null default current_timestamp,
  published_at datetime null,
  last_error varchar(500) null,
  created_at datetime not null default current_timestamp,
  key idx_outbox_pending (status, next_attempt_at)
);
```

PGvector uses `vector(1024)`, a unique `(source, content_checksum)` constraint, GIN keyword index and HNSW cosine index.

- [ ] **Step 5: Verify GREEN and idempotency**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=LeaseMigrationIT test
.\mvnw.cmd -pl web/web-app -Dtest=LeaseMigrationIT test
```

Expected: both runs pass; the second run does not duplicate seed rows.

- [ ] **Step 6: Commit**

```powershell
git add web/web-app/src/main/resources/db db/pgvector db/ai-rental-agent/pgvector-schema.sql web/web-app/src/test/java/com/atguigu/lease/migration/LeaseMigrationIT.java
git commit -m "feat: add reproducible lease demo data"
```

### Task 3: 增加 Docker Compose 与服务健康检查

**Files:**
- Create: `compose.yaml`
- Create: `Dockerfile`
- Create: `docker/minio/init.sh`
- Create: `docker/minio/demo-room.jpg`
- Create: `scripts/verify-compose.ps1`
- Modify: `.dockerignore`
- Test: `scripts/verify-compose.ps1`

**Interfaces:**
- Produces: services `mysql`, `redis`, `rabbitmq`, `pgvector`, `minio`, `minio-init`, `web-app`; health URL `http://localhost:8081/actuator/health`.

- [ ] **Step 1: Write a failing compose verifier**

```powershell
$required = @('mysql','redis','rabbitmq','pgvector','minio','web-app')
$services = docker compose config --services
foreach ($name in $required) {
  if ($services -notcontains $name) { throw "missing compose service: $name" }
}
$health = Invoke-RestMethod 'http://localhost:8081/actuator/health'
if ($health.status -ne 'UP') { throw "web-app health is not UP" }
```

- [ ] **Step 2: Run RED**

```powershell
.\scripts\verify-compose.ps1
```

Expected: FAIL because `compose.yaml` does not exist.

- [ ] **Step 3: Implement Compose and container image**

Use pinned images `mysql:8.4`, `redis:7.4-alpine`, `rabbitmq:3.13-management-alpine`, `pgvector/pgvector:pg16`, and `minio/minio:RELEASE.2025-07-23T15-54-02Z`. Build backend with `maven:3.9.9-eclipse-temurin-21`, run with `eclipse-temurin:21-jre`, and gate `web-app` on healthy infrastructure.

Compose passes the environment contract from Task 1 and mounts the PGvector scripts. MySQL schema remains owned by Flyway inside `web-app`.

- [ ] **Step 4: Verify GREEN**

```powershell
docker compose config
docker compose up -d --build mysql redis rabbitmq pgvector minio minio-init web-app
.\scripts\verify-compose.ps1
docker compose ps
```

Expected: verifier exits 0 and every long-running service is healthy.

- [ ] **Step 5: Commit**

```powershell
git add compose.yaml Dockerfile .dockerignore docker/minio scripts/verify-compose.ps1
git commit -m "build: add reproducible service stack"
```

### Task 4: 实现仅在 demo 环境启用的固定验证码登录

**Files:**
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/config/DemoLoginProperties.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/VerificationCodeService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/RedisVerificationCodeService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/DemoVerificationCodeService.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/LoginServiceImpl.java`
- Modify: `web/web-app/src/main/resources/application.yml`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/impl/DemoVerificationCodeServiceTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/impl/LoginServiceImplTest.java`

**Interfaces:**
- Produces: `VerificationCodeService.issue(String phone)` and `VerificationCodeService.verify(String phone, String code)`.

- [ ] **Step 1: Write RED tests**

```java
@Test
void acceptsFixedCodeOnlyForConfiguredDemoPhone() {
    var service = new DemoVerificationCodeService("13800000000", "888888");
    assertThat(service.verify("13800000000", "888888")).isTrue();
    assertThat(service.verify("13900000000", "888888")).isFalse();
}
```

Also assert `RedisVerificationCodeService` remains the selected bean when `app.demo-login.enabled=false`.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=DemoVerificationCodeServiceTest,LoginServiceImplTest test
```

Expected: FAIL because the verification abstraction does not exist.

- [ ] **Step 3: Implement and inject the strategy**

Use `@ConditionalOnProperty(name="app.demo-login.enabled", havingValue="true")` for demo and `matchIfMissing=true` inverse condition for Redis/SMS. `LoginServiceImpl` no longer reads Redis directly for validation.

- [ ] **Step 4: Verify GREEN and regression**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=DemoVerificationCodeServiceTest,LoginServiceImplTest,SmsServiceImplTest test
```

- [ ] **Step 5: Commit**

```powershell
git add web/web-app/src/main/java/com/atguigu/lease/web/app/config web/web-app/src/main/java/com/atguigu/lease/web/app/service web/web-app/src/test/java/com/atguigu/lease/web/app/service web/web-app/src/main/resources/application.yml
git commit -m "feat: add isolated demo login mode"
```

### Task 5: 建立 RAG 文档、切片、PGvector 检索和关键词降级

**Files:**
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/knowledge/KnowledgeDocumentLoader.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/knowledge/KnowledgeChunker.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/knowledge/PgVectorKnowledgeRepository.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/knowledge/KeywordKnowledgeRetriever.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/knowledge/HybridRentalKnowledgeService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/config/PgVectorDataSourceConfiguration.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/RentalKnowledgeService.java`
- Remove: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/impl/InMemoryRentalKnowledgeService.java`
- Modify: `docs/ai-rental-agent/rag-knowledge.md`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/knowledge/KnowledgeChunkerTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/knowledge/HybridRentalKnowledgeServiceTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/knowledge/PgVectorKnowledgeRepositoryIT.java`

**Interfaces:**
- Produces: `List<RentalKnowledgeChunk> search(String query, int limit)`; idempotent `indexChangedDocuments()`.

- [ ] **Step 1: Write RED tests for heading-aware chunks and fallback**

```java
@Test
void preservesSourceAndHeadingWhenChunkingMarkdown() {
    List<RentalKnowledgeChunk> chunks = chunker.chunk("deposit.md", "# 押金\n退租验收后按合同退还。", 200);
    assertThat(chunks).singleElement().satisfies(chunk -> {
        assertThat(chunk.getTitle()).isEqualTo("押金");
        assertThat(chunk.getSource()).isEqualTo("deposit.md");
        assertThat(chunk.getContent()).contains("退租验收");
    });
}

@Test
void fallsBackToKeywordRetrieverWhenVectorSearchFails() {
    when(vector.search("押金怎么退", 3)).thenThrow(new DataAccessResourceFailureException("pg down"));
    assertThat(service.search("押金怎么退", 3)).extracting(RentalKnowledgeChunk::getTitle).contains("押金与退还");
}
```

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=KnowledgeChunkerTest,HybridRentalKnowledgeServiceTest test
```

- [ ] **Step 3: Implement checksum-based ingestion and retrieval**

Use SHA-256 over normalized source/title/content, `ON CONFLICT (source, content_checksum) DO UPDATE`, 1024-dimensional embeddings, top-k bounded to 10, and a configurable similarity threshold. The keyword retriever loads the same Markdown source and returns the same citation DTO.

- [ ] **Step 4: Verify unit and PGvector integration tests**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=KnowledgeChunkerTest,HybridRentalKnowledgeServiceTest,PgVectorKnowledgeRepositoryIT test
```

Expected: first indexing inserts chunks, second indexing inserts zero new rows, and fallback test passes without PostgreSQL.

- [ ] **Step 5: Commit**

```powershell
git add web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai web/web-app/src/main/java/com/atguigu/lease/web/app/config/PgVectorDataSourceConfiguration.java web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai docs/ai-rental-agent/rag-knowledge.md
git commit -m "feat: add hybrid pgvector rental knowledge"
```

### Task 6: 实现受控房源工具和 GLM/Fallback Agent

**Files:**
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/RentalAgent.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/AgentAnswer.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/AgentMode.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/AgentExecutionRegistry.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/ModelRentalAgent.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/agent/FallbackRentalAgent.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/tool/RentalReadTools.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/impl/RentalRoomToolServiceImpl.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/AiRecommendedRoomVo.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/agent/AgentExecutionRegistryTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/agent/FallbackRentalAgentTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/agent/ModelRentalAgentTest.java`

**Interfaces:**
- Produces: `AgentAnswer answer(AgentRequest request)`; read-only tools `searchAvailableRooms(RoomToolRequest)` and `searchRentalKnowledge(KnowledgeToolRequest)`.

- [ ] **Step 1: Write RED tests for tool safety and model fallback**

```java
@Test
void modelFailureReturnsFallbackWithRealToolResults() {
    when(chatClient.prompt()).thenThrow(new RuntimeException("provider timeout"));
    AgentAnswer answer = agent.answer(new AgentRequest("预算2500并说明押金", preferences));
    assertThat(answer.mode()).isEqualTo(AgentMode.FALLBACK);
    assertThat(answer.rooms()).allMatch(room -> room.getRoomId() != null);
    assertThat(answer.citations()).isNotEmpty();
}
```

Add concurrency coverage proving `AgentExecutionRegistry` never mixes room IDs or citation IDs between simultaneous executions.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AgentExecutionRegistryTest,FallbackRentalAgentTest,ModelRentalAgentTest test
```

- [ ] **Step 3: Implement Spring AI tool calling**

Register only these model-visible tools:

```java
@Tool(description = "查询当前真实可租房源，只读")
public List<AiRecommendedRoomVo> searchAvailableRooms(RoomToolRequest request) { ... }

@Tool(description = "检索租房规则知识并返回来源，只读")
public List<AiCitationVo> searchRentalKnowledge(KnowledgeToolRequest request) { ... }
```

The system prompt forbids appointment mutation and instructs the model to use tool results. `ModelRentalAgent` validates final structured IDs against the current execution registry; unknown IDs are discarded. A circuit-breaker-style timeout routes to `FallbackRentalAgent`.

- [ ] **Step 4: Verify GREEN and existing parser regressions**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AgentExecutionRegistryTest,FallbackRentalAgentTest,ModelRentalAgentTest,RentalIntentParserTest test
```

- [ ] **Step 5: Commit**

```powershell
git add web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/AiRecommendedRoomVo.java web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai
git commit -m "feat: add controlled GLM rental agent"
```

### Task 7: 持久化会话并收敛 AI API 合同

**Files:**
- Create: `model/src/main/java/com/atguigu/lease/model/entity/AiConversation.java`
- Create: `model/src/main/java/com/atguigu/lease/model/entity/AiMessage.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/mapper/AiConversationMapper.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/mapper/AiMessageMapper.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/AiConversationService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/impl/AiConversationServiceImpl.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/controller/ai/AiChatController.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/AiChatRequestVo.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/AiChatResponseVo.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/impl/AiConversationServiceImplTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/controller/ai/AiChatControllerIT.java`

**Interfaces:**
- Produces: `POST /app/ai/chat`; `GET /app/ai/sessions/{sessionId}`; response fields from the spec including `messageId` and `mode`.

- [ ] **Step 1: Write RED ownership and history tests**

```java
@Test
void rejectsReadingAnotherUsersConversation() {
    UUID sessionId = repository.create(userOne);
    assertThatThrownBy(() -> service.history(userTwo, sessionId))
        .isInstanceOf(LeaseException.class)
        .hasMessageContaining("无权访问会话");
}
```

Controller integration coverage sends two messages under one session and expects ordered history containing both user and assistant messages.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AiConversationServiceImplTest,AiChatControllerIT test
```

- [ ] **Step 3: Implement conversation ownership and message persistence**

Use UUID session IDs, MySQL ownership as source of truth, Redis as a bounded history cache, and a maximum of 10 prior turns in model context. Persist fallback responses exactly like model responses.

- [ ] **Step 4: Verify GREEN**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AiChatRequestValidatorTest,AiConversationServiceImplTest,AiChatControllerIT test
```

- [ ] **Step 5: Commit**

```powershell
git add model/src/main/java/com/atguigu/lease/model/entity/AiConversation.java model/src/main/java/com/atguigu/lease/model/entity/AiMessage.java web/web-app/src/main/java/com/atguigu/lease/web/app/mapper web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai web/web-app/src/main/java/com/atguigu/lease/web/app/controller/ai web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai web/web-app/src/test/java/com/atguigu/lease/web/app
git commit -m "feat: persist rental agent conversations"
```

### Task 8: 实现预约草稿、一次性确认和并发幂等

**Files:**
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/controller/ai/AiAppointmentController.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/appointment/AppointmentDraftService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/appointment/AppointmentConfirmationService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/appointment/RedisAppointmentDraftStore.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/appointment/AppointmentDraftRequest.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/appointment/AppointmentDraftResponse.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/appointment/AppointmentConfirmRequest.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/appointment/AppointmentConfirmResponse.java`
- Create: `model/src/main/java/com/atguigu/lease/model/entity/AiAppointmentIdempotency.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ViewAppointmentService.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/ViewAppointmentServiceImpl.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/appointment/AppointmentDraftServiceTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/appointment/AppointmentConfirmationServiceIT.java`

**Interfaces:**
- Produces: `POST /app/ai/appointments/draft`; `POST /app/ai/appointments/confirm`; `createConfirmedAppointment(long userId, AppointmentDraft draft, String tokenHash)`.

- [ ] **Step 1: Write RED validation and idempotency tests**

```java
@Test
void concurrentConfirmationCreatesExactlyOneAppointment() throws Exception {
    String token = drafts.create(userId, validDraft).confirmationToken();
    List<AppointmentConfirmResponse> results = runConcurrently(2,
        () -> confirmations.confirm(userId, token));
    assertThat(appointmentCount(userId, validDraft.roomId())).isEqualTo(1);
    assertThat(results).extracting(AppointmentConfirmResponse::appointmentId).containsOnly(results.getFirst().appointmentId());
    assertThat(results).extracting(AppointmentConfirmResponse::idempotentReplay).containsExactlyInAnyOrder(false, true);
}
```

Add tests for expired token, wrong user, past time, unavailable room, malformed phone and duplicate active appointment.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AppointmentDraftServiceTest,AppointmentConfirmationServiceIT test
```

- [ ] **Step 3: Implement two-phase confirmation**

Store only a SHA-256 token hash in durable idempotency rows. Redis value includes user ID and normalized immutable draft. Use Redis Lua or `SET NX` lock for atomic transition `PENDING -> PROCESSING`; transaction inserts appointment, idempotency row and Outbox row. On duplicate-key race, load and return the existing appointment with `idempotentReplay=true`.

- [ ] **Step 4: Verify GREEN and API behavior**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AppointmentDraftServiceTest,AppointmentConfirmationServiceIT test
```

- [ ] **Step 5: Commit**

```powershell
git add model/src/main/java/com/atguigu/lease/model/entity/AiAppointmentIdempotency.java web/web-app/src/main/java/com/atguigu/lease/web/app/controller/ai web/web-app/src/main/java/com/atguigu/lease/web/app/service web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/appointment web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/appointment
git commit -m "feat: add confirmed idempotent AI appointments"
```

### Task 9: 用 Transactional Outbox 完成 RabbitMQ 最终投递

**Files:**
- Create: `model/src/main/java/com/atguigu/lease/model/entity/AppointmentEventOutbox.java`
- Create: `common/src/main/java/com/atguigu/lease/outbox/AppointmentOutboxPublisher.java`
- Create: `common/src/main/java/com/atguigu/lease/outbox/AppointmentOutboxRepository.java`
- Create: `common/src/main/java/com/atguigu/lease/outbox/OutboxPublishScheduler.java`
- Modify: `common/src/main/java/com/atguigu/lease/config/RabbitMQConfig.java`
- Modify: `common/src/main/java/com/atguigu/lease/consumer/appointment/AppointmentMessageConsumer.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/outbox/AppointmentOutboxPublisherIT.java`

**Interfaces:**
- Consumes: `appointment_event_outbox` rows created in Task 8.
- Produces: publisher confirms; states `PENDING`, `PUBLISHED`, `DEAD`; exponential retry with maximum 5 attempts.

- [ ] **Step 1: Write RED recovery test**

```java
@Test
void pendingEventPublishesAfterRabbitRecovers() {
    long eventId = insertPendingOutbox();
    rabbit.stop();
    publisher.publishBatch();
    assertThat(status(eventId)).isEqualTo("PENDING");
    rabbit.start();
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
        publisher.publishBatch();
        assertThat(status(eventId)).isEqualTo("PUBLISHED");
    });
}
```

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AppointmentOutboxPublisherIT test
```

- [ ] **Step 3: Implement publisher confirms and retry state**

Claim rows with `SELECT ... FOR UPDATE SKIP LOCKED`, publish with event ID as correlation ID, mark published only after broker confirm, persist a truncated error and next attempt time on failure, and mark `DEAD` after five failures. Consumer logs and deduplicates by event ID.

- [ ] **Step 4: Verify GREEN**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AppointmentOutboxPublisherIT test
```

- [ ] **Step 5: Commit**

```powershell
git add model/src/main/java/com/atguigu/lease/model/entity/AppointmentEventOutbox.java common/src/main/java/com/atguigu/lease/outbox common/src/main/java/com/atguigu/lease/config/RabbitMQConfig.java common/src/main/java/com/atguigu/lease/consumer/appointment/AppointmentMessageConsumer.java web/web-app/src/test/java/com/atguigu/lease/outbox
git commit -m "feat: reliably publish appointment events"
```

### Task 10: 将 H5 纳入仓库并建立前端测试基线

**Files:**
- Create: `frontend/rent-house-h5/**` copied from `E:\frontend\rentHouseH5\rentHouseH5` excluding `node_modules`, `dist`, `.env.*` secrets and generated caches
- Modify: `frontend/rent-house-h5/package.json`
- Create: `frontend/rent-house-h5/vitest.config.ts`
- Create: `frontend/rent-house-h5/playwright.config.ts`
- Create: `frontend/rent-house-h5/Dockerfile`
- Create: `frontend/rent-house-h5/nginx.conf`
- Modify: `compose.yaml`
- Test: `frontend/rent-house-h5/src/App.spec.ts`

**Interfaces:**
- Produces: `npm run type-check`, `npm run test:unit`, `npm run build`, H5 service at `http://localhost:5173`.

- [ ] **Step 1: Copy the clean source tree**

Use a file manifest from `rg --files` and copy source/config/assets only. Do not copy the original `node_modules`, `dist`, local `.env.development`, local `.env.production`, or Git metadata. Preserve the original MIT license.

- [ ] **Step 2: Write and run a RED frontend test**

```ts
import { mount } from '@vue/test-utils'
import App from './App.vue'

it('mounts the H5 application shell', () => {
  expect(mount(App, { global: { stubs: ['router-view'] } }).exists()).toBe(true)
})
```

Run:

```powershell
Set-Location frontend/rent-house-h5
npm run test:unit
```

Expected: FAIL because Vitest scripts and dependencies are absent.

- [ ] **Step 3: Add Vitest, Playwright and production container**

Add scripts `test:unit`, `test:e2e`, and `type-check`; pin a Node 20 build image. Nginx serves the built SPA and proxies `/app` to `web-app:8081`.

- [ ] **Step 4: Verify GREEN**

```powershell
npm ci
npm run type-check
npm run test:unit
npm run build
Set-Location ..\..
docker compose config
```

- [ ] **Step 5: Commit**

```powershell
git add frontend/rent-house-h5 compose.yaml
git commit -m "build: integrate tenant H5 application"
```

### Task 11: 实现 H5 AI 对话、房源卡片和预约确认体验

**Files:**
- Create: `frontend/rent-house-h5/src/api/ai/index.ts`
- Create: `frontend/rent-house-h5/src/api/ai/types.ts`
- Create: `frontend/rent-house-h5/src/views/aiAssistant/aiAssistant.vue`
- Create: `frontend/rent-house-h5/src/components/AiRoomCard/AiRoomCard.vue`
- Create: `frontend/rent-house-h5/src/components/AiCitationList/AiCitationList.vue`
- Create: `frontend/rent-house-h5/src/components/AppointmentDraftSheet/AppointmentDraftSheet.vue`
- Modify: `frontend/rent-house-h5/src/router/otherRoutes.ts`
- Modify: `frontend/rent-house-h5/src/views/message/message.vue`
- Test: `frontend/rent-house-h5/src/views/aiAssistant/aiAssistant.spec.ts`
- Test: `frontend/rent-house-h5/src/components/AppointmentDraftSheet/AppointmentDraftSheet.spec.ts`

**Interfaces:**
- Consumes: Task 7 and Task 8 API contracts.
- Produces: `/ai-assistant` route; persistent session ID; navigation to `/roomDetail?id=...` and `/myAppointment`.

- [ ] **Step 1: Write RED component tests**

```ts
it('requires explicit confirmation before calling confirm API', async () => {
  const wrapper = mount(AppointmentDraftSheet, { props: { room, open: true } })
  await wrapper.find('[data-test="create-draft"]').trigger('click')
  expect(createDraft).toHaveBeenCalledTimes(1)
  expect(confirmAppointment).not.toHaveBeenCalled()
  await wrapper.find('[data-test="confirm-appointment"]').trigger('click')
  expect(confirmAppointment).toHaveBeenCalledTimes(1)
})
```

Add assertions that MODEL/FALLBACK labels render, citations display source names, and recommended room cards use stable dimensions.

- [ ] **Step 2: Run RED**

```powershell
Set-Location frontend/rent-house-h5
npm run test:unit -- aiAssistant AppointmentDraftSheet
```

- [ ] **Step 3: Implement the H5 flow**

Use Vant icons, list/card primitives and an action sheet. Keep the message composer above the safe-area inset, disable submit while pending, preserve unsent text after network failure, and never call confirmation before the explicit button event. Use existing auth interceptor and room/appointment routes.

- [ ] **Step 4: Verify GREEN, types and build**

```powershell
npm run test:unit
npm run type-check
npm run build
```

- [ ] **Step 5: Commit**

```powershell
Set-Location ..\..
git add frontend/rent-house-h5/src
git commit -m "feat: add tenant AI rental assistant UI"
```

### Task 12: 完成 API、Compose 和 Playwright 端到端验收

**Files:**
- Create: `web/web-app/src/test/java/com/atguigu/lease/e2e/RentalAgentClosedLoopIT.java`
- Create: `frontend/rent-house-h5/e2e/rental-agent-closed-loop.spec.ts`
- Create: `scripts/smoke-ai-agent.ps1`
- Modify: `compose.yaml`
- Modify: `readme.md`
- Modify: `docs/ai-rental-agent/api.md`
- Modify: `docs/ai-rental-agent/test-report.md`

**Interfaces:**
- Produces: one-command stack; API smoke script; browser flow proving the spec acceptance criteria.

- [ ] **Step 1: Write RED API and browser journeys**

API integration journey:

```java
@Test
void fallbackChatToConfirmedAppointmentIsClosedLoop() {
    String token = login("13800000000", "888888");
    AiChatResponse chat = chat(token, "预算2500并说明押金怎么退");
    assertThat(chat.mode()).isEqualTo(FALLBACK);
    assertThat(chat.recommendedRooms()).isNotEmpty();
    DraftResponse draft = draft(token, chat.recommendedRooms().getFirst().roomId(), tomorrowAt(14, 0));
    ConfirmResponse first = confirm(token, draft.confirmationToken());
    ConfirmResponse replay = confirm(token, draft.confirmationToken());
    assertThat(replay.appointmentId()).isEqualTo(first.appointmentId());
    assertThat(listAppointments(token)).extracting("id").contains(first.appointmentId());
}
```

Playwright journey uses mobile viewport `390x844`, completes the same flow in H5 and captures the assistant page and final appointment page.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=RentalAgentClosedLoopIT test
Set-Location frontend/rent-house-h5
npx playwright test e2e/rental-agent-closed-loop.spec.ts
```

- [ ] **Step 3: Close integration gaps and document exact operations**

`scripts/smoke-ai-agent.ps1` obtains a demo token, posts a mixed query, drafts and confirms an appointment twice, and asserts one appointment ID. README documents:

```powershell
Copy-Item .env.example .env
docker compose up --build
.\scripts\smoke-ai-agent.ps1
docker compose down
docker compose down --volumes  # documented destructive demo reset only
```

The destructive reset command must remain documentation-only unless the user explicitly requests a reset.

- [ ] **Step 4: Run complete verification from a clean demo stack**

```powershell
.\mvnw.cmd clean verify
Set-Location frontend/rent-house-h5
npm ci
npm run type-check
npm run test:unit
npm run build
Set-Location ..\..
docker compose up -d --build
.\scripts\verify-compose.ps1
.\scripts\smoke-ai-agent.ps1
Set-Location frontend/rent-house-h5
npx playwright test
```

Expected: Maven reports zero failures; H5 type/build/unit suites exit 0; every Compose service is healthy; API smoke passes; Playwright passes on mobile and desktop; screenshots show no overlap.

- [ ] **Step 5: Run secret and worktree audit**

```powershell
Set-Location ..\..
rg -n "api-key:\s*[^$]|GLM_API_KEY=.+|access-key-secret:\s*[^$]" . -g '!target/**' -g '!node_modules/**' -g '!.env'
git status --short
git diff --check
```

Expected: no committed secret match; `.idea/misc.xml` remains the only pre-existing unrelated modification.

- [ ] **Step 6: Commit documentation and final tests**

```powershell
git add web/web-app/src/test/java/com/atguigu/lease/e2e frontend/rent-house-h5/e2e scripts/smoke-ai-agent.ps1 compose.yaml readme.md docs/ai-rental-agent
git commit -m "test: verify AI rental appointment closed loop"
```

## Plan Completion Gate

Before declaring implementation complete:

- Re-read `docs/superpowers/specs/2026-08-03-ai-rental-agent-closed-loop-design.md` and map every acceptance criterion to fresh command output.
- Confirm every task checkbox is complete and every task has its own commit.
- Confirm no test was added only after its production behavior.
- Confirm `.idea/misc.xml` is neither staged nor committed.
- Confirm no command required to prove completion is still running.
