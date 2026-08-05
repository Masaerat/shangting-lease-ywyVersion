# 27公寓 agentRag 闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `agentRag` 已有企业级 RAG 和 SSE 对话能力上，补齐 Docker 可复现运行、无 Key 降级、登录、预约二次确认、Transactional Outbox、H5 展示和端到端验收。

**Architecture:** 保留现有 Java 21 / Spring Boot 3.4.1 / Spring AI 1.0.0、`AiModelConfiguration`、PGvector 第二数据源、Admin 文档/房源入库、`RoomSearchTool`、`RentalChatServiceImpl` 和 SSE 协议。新增模型与 fallback 两个聊天引擎；模型模式继续使用现有 ChatClient + VectorStore + Tool，fallback 模式使用 MySQL + 本地知识。预约写入通过 Redis 草稿令牌、MySQL 幂等记录和 Outbox 完成。

**Tech Stack:** Java 21, Spring Boot 3.4.1, Spring AI 1.0.0, MyBatis-Plus 3.5.9, Testcontainers 2.0.5, MySQL 8.4, Redis 7.4, RabbitMQ 3.13, PostgreSQL 16 + PGvector, MinIO, Vue 3, Vant, Vitest, Playwright, Docker Compose.

## Global Constraints

- 只在 `agentRag` 分支实施；开始每个任务前运行 `git branch --show-current` 并确认输出为 `agentRag`。
- 现有 `agentRag` 的 AI/RAG/Admin 实现是基线，不重新创建另一套 Agent、VectorStore 或知识管理服务。
- 保留 Spring Boot 3.4.1、Spring AI 1.0.0 和现有 GLM 原生路径、双 Key、1024 维 embedding 配置，除非测试证明必须修改。
- 默认命令为 `docker compose up --build`；空数据卷必须可完成 fallback 闭环。
- `.env` 被 Git 忽略；只提交无秘密的 `.env.example` 和 `${ENV_VAR}` 配置。
- SSE `POST /app/ai/chat` 保持兼容；模型模式和 fallback 模式使用相同事件结构。
- 模型仅能调用现有只读 `RoomSearchTool`；聊天内容不能直接创建预约。
- 预约必须经过 10 分钟一次性令牌和独立确认接口；重复确认返回同一预约。
- 预约与 Outbox 同一 MySQL 事务提交；RabbitMQ 不可用时不丢事件。
- H5 源码复制到本仓库 `frontend/rent-house-h5`，不修改原目录 `E:\frontend\rentHouseH5\rentHouseH5`。
- 不修改 Admin 前端，不自动签约或支付，不提交真实数据。
- 不暂存或提交用户已有 `.idea/misc.xml`；禁止 `git reset --hard` 和 `git checkout --`。
- 每个行为执行 RED -> GREEN -> REFACTOR，并在任务末尾单独提交。

---

### Task 1: 审计现有 agentRag 基线并补齐可复现构建入口

**Files:**
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
- Create: `.env.example`
- Create: `web/web-app/src/main/resources/application-docker.yml`
- Create: `web/web-admin/src/main/resources/application-docker.yml`
- Modify: `.gitignore`
- Test: existing `RoomKnowledgeServiceImplTest`, `DocumentKnowledgeServiceImplTest`, `RentalChatServiceImplTest`

**Interfaces:**
- Produces: Maven 3.9.9 Wrapper; `docker` Spring profile; environment contract for MySQL、Redis、RabbitMQ、MinIO、GLM and PGvector.

- [ ] **Step 1: Verify branch and record baseline**

```powershell
git branch --show-current
.\mvnw.cmd -version  # expected to fail before wrapper exists
C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd clean compile
C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd test -Dtest=RoomKnowledgeServiceImplTest,DocumentKnowledgeServiceImplTest,RentalChatServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false -pl web/web-admin,web/web-app -am
```

Expected: branch is `agentRag`; wrapper command is RED; existing compile and six AI assertions remain GREEN.

- [ ] **Step 2: Generate Maven Wrapper**

```powershell
C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd wrapper:wrapper -Dmaven=3.9.9
```

- [ ] **Step 3: Add docker-profile configuration without touching local ignored config**

Both `application-docker.yml` files use environment variables. The AI block keeps the existing property names:

```yaml
spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://mysql:3306/lease?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true}
    username: ${MYSQL_USER:lease}
    password: ${MYSQL_PASSWORD:lease}
  ai:
    openai:
      base-url: ${AI_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
      api-key: ${AI_CHAT_API_KEY:}
      completions-path: /chat/completions
      embeddings-path: /embeddings
      embedding:
        api-key: ${AI_EMBED_API_KEY:${AI_CHAT_API_KEY:}}
        options:
          model: ${AI_EMBED_MODEL:embedding-3}
          dimensions: 1024
app.datasource.pg:
  url: ${PG_URL:jdbc:postgresql://pgvector:5432/lease_vec}
  username: ${PG_USER:lease}
  password: ${PG_PASSWORD:lease}
```

`.env.example` has demo infrastructure values and empty AI keys. Existing ignored `application.yml` files are not deleted or committed.

- [ ] **Step 4: Verify GREEN**

```powershell
.\mvnw.cmd -version
.\mvnw.cmd clean compile
.\mvnw.cmd test -Dtest=RoomKnowledgeServiceImplTest,DocumentKnowledgeServiceImplTest,RentalChatServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false -pl web/web-admin,web/web-app -am
git diff --check
```

- [ ] **Step 5: Commit only baseline files**

```powershell
git add .mvn mvnw mvnw.cmd .env.example .gitignore web/web-app/src/main/resources/application-docker.yml web/web-admin/src/main/resources/application-docker.yml
git commit -m "build: add reproducible agentRag configuration"
```

### Task 2: 固化 MySQL 迁移、演示数据和新增闭环表

**Files:**
- Modify: `pom.xml`
- Modify: `web/web-app/pom.xml`
- Create: `web/web-app/src/main/resources/application-default.yml`
- Modify: `web/web-app/src/main/resources/application-docker.yml`
- Create: `web/web-app/src/main/resources/db/migration/V1__lease_baseline.sql`
- Create: `web/web-app/src/main/resources/db/migration/V2__agent_closed_loop.sql`
- Create: `web/web-app/src/main/resources/db/migration/V3__demo_seed.sql`
- Create: `db/pgvector/001-init.sql`
- Test: `web/web-app/src/test/java/com/atguigu/lease/migration/LeaseMigrationIT.java`

**Interfaces:**
- Produces: a DDL-only snapshot of the existing lease tables for a fresh isolated Docker database; new `ai_appointment_idempotency` and `appointment_event_outbox`; six fabricated demo rooms and demo user `13800000000`.
- Safety boundary: the default local profile baselines the existing `lease` schema at V1 and applies only incremental V2+ migrations. The isolated Docker profile executes V1+ against an empty database and refuses non-empty schemas without Flyway history.

- [x] **Step 1: Add Testcontainers migration test and run RED**

```java
@Testcontainers(disabledWithoutDocker = true)
class LeaseMigrationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("lease").withUsername("lease").withPassword("lease");

    @Test
    void emptyDatabaseContainsDemoInventoryAndClosedLoopTables() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "lease", "lease").load().migrate();
        assertThat(count("room_info")).isEqualTo(6);
        assertThat(countWhere("user_info", "phone='13800000000'")).isEqualTo(1);
        assertThat(count("ai_appointment_idempotency")).isZero();
        assertThat(count("appointment_event_outbox")).isZero();
    }
}
```

Run:

```powershell
.\mvnw.cmd -pl web/web-app -am "-Dtest=LeaseMigrationIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: RED because Flyway dependencies/migrations are absent.

- [x] **Step 2: Export DDL only and sanitize it**

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe' --protocol=tcp --host=localhost --user=root --password=123456 --no-data --skip-comments --skip-dump-date --result-file='web\web-app\src\main\resources\db\migration\V1__lease_baseline.sql' lease
```

Remove host definers and `CREATE DATABASE` statements. Keep the existing `ai_knowledge_doc` DDL consistent with `db/ai-rental-agent/ai_knowledge_doc.sql`.

- [x] **Step 3: Add closed-loop tables and fabricated seed data**

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

`V3__demo_seed.sql` uses fixed IDs and `INSERT ... ON DUPLICATE KEY UPDATE` for two regions, three apartments, six rooms, labels, payment types, images, one unavailable room and one demo user. It contains no local user rows.

- [x] **Step 4: Verify GREEN and repeatability**

```powershell
.\mvnw.cmd -pl web/web-app -am "-Dtest=LeaseMigrationIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\mvnw.cmd -pl web/web-app -am "-Dtest=LeaseMigrationIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [x] **Step 5: Commit**

```powershell
git add pom.xml web/web-app/pom.xml web/web-app/src/main/resources/application-default.yml web/web-app/src/main/resources/application-docker.yml web/web-app/src/main/resources/db db/pgvector web/web-app/src/test/java/com/atguigu/lease/migration docs/superpowers/plans/2026-08-03-ai-rental-agent-closed-loop.md
git commit -m "feat: add reproducible agent demo database"
```

### Task 3: 编排 MySQL、Redis、RabbitMQ、MinIO、PGvector、Admin 和 App

**Files:**
- Create: `compose.yaml`
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `docker/minio/init.sh`
- Create: `docker/minio/demo-room.jpg`
- Create: `scripts/verify-compose.ps1`
- Modify: `web/pom.xml`
- Test: `scripts/verify-compose.ps1`

**Interfaces:**
- Produces: services `mysql`, `redis`, `rabbitmq`, `pgvector`, `minio`, `minio-init`, `web-admin`, `web-app`; Actuator health on ports 8080 and 8081.

- [x] **Step 1: Write verifier and run RED**

```powershell
$required = @('mysql','redis','rabbitmq','pgvector','minio','web-admin','web-app')
$services = docker compose config --services
foreach ($name in $required) { if ($services -notcontains $name) { throw "missing: $name" } }
foreach ($port in 8080,8081) {
  $health = Invoke-RestMethod "http://localhost:$port/actuator/health"
  if ($health.status -ne 'UP') { throw "port $port is not UP" }
}
```

Expected: RED because Compose does not exist.

- [x] **Step 2: Add pinned images and multi-module Java image**

Use `mysql:8.4`, `redis:7.4-alpine`, `rabbitmq:3.13-management-alpine`, `pgvector/pgvector:pg16`, MinIO pinned release, `maven:3.9.9-eclipse-temurin-21` builder and `eclipse-temurin:21-jre` runtime. Build `web-admin` and `web-app` separately from one Dockerfile `APP_MODULE` argument.

`web-app` and `web-admin` run with `SPRING_PROFILES_ACTIVE=docker`; infrastructure services have real health checks and application services wait for healthy dependencies.

- [x] **Step 3: Verify GREEN**

```powershell
docker compose config
docker compose up -d --build mysql redis rabbitmq pgvector minio minio-init web-admin web-app
.\scripts\verify-compose.ps1
docker compose ps
```

- [x] **Step 4: Commit**

```powershell
git add compose.yaml Dockerfile .dockerignore docker scripts/verify-compose.ps1
git commit -m "build: containerize agentRag services"
```

### Task 4: 增加隔离的 demo 固定验证码登录

**Files:**
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/config/DemoLoginProperties.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/VerificationCodeService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/RedisVerificationCodeService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/DemoVerificationCodeService.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/LoginServiceImpl.java`
- Modify: `web/web-app/src/main/resources/application-docker.yml`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/impl/DemoVerificationCodeServiceTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/impl/LoginServiceImplTest.java`

**Interfaces:**
- Produces: `VerificationCodeService.issue(phone)` and `verify(phone, code)`; demo phone `13800000000`, code `888888` only when `app.demo-login.enabled=true`.

- [x] **Step 1: Write RED tests**

```java
@Test
void fixedCodeIsLimitedToConfiguredDemoPhone() {
    var service = new DemoVerificationCodeService("13800000000", "888888");
    assertThat(service.verify("13800000000", "888888")).isTrue();
    assertThat(service.verify("13900000000", "888888")).isFalse();
}
```

Also load Spring context twice and assert demo bean is selected only when the property is true.

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=DemoVerificationCodeServiceTest,LoginServiceImplTest test
```

- [x] **Step 3: Implement strategy injection**

Move Redis/SMS issue and verify behavior out of `LoginServiceImpl`. Use conditional beans; non-demo behavior remains unchanged.

- [x] **Step 4: Verify GREEN and SMS regression**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=DemoVerificationCodeServiceTest,LoginServiceImplTest,SmsServiceImplTest test
```

- [x] **Step 5: Commit**

```powershell
git add web/web-app/src/main/java/com/atguigu/lease/web/app/config/DemoLoginProperties.java web/web-app/src/main/java/com/atguigu/lease/web/app/service/VerificationCodeService.java web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl web/web-app/src/main/resources/application-docker.yml web/web-app/src/test/java/com/atguigu/lease/web/app/service/impl
git commit -m "feat: add docker-only demo login"
```

### Task 5: 让现有 SSE Agent 支持无 Key 启动、fallback 和用户隔离

**Files:**
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/RentalChatEngine.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/impl/ModelRentalChatEngine.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/impl/FallbackRentalChatEngine.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/impl/LocalRentalKnowledgeService.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/AiRecommendationVo.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/AiChatMetaVo.java`
- Create: `web/web-app/src/main/resources/ai/rag-knowledge.md`
- Create: `common/src/main/java/com/atguigu/lease/config/ai/AiModelAvailableCondition.java`
- Modify: `common/src/main/java/com/atguigu/lease/config/ai/AiModelConfiguration.java`
- Modify: `Dockerfile`
- Modify: `compose.yaml`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai/impl/RentalChatServiceImpl.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/ChatSseEvent.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/tools/RoomSearchTool.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/config/ai/ChatClientConfiguration.java`
- Modify: `common/src/main/java/com/atguigu/lease/config/ai/PgVectorDataSourceConfiguration.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/impl/FallbackRentalChatEngineTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/impl/RentalChatServiceImplTest.java`

**Interfaces:**
- Produces: `RentalChatEngine.mode()` and `chat(ChatExecution execution, Consumer<ChatSseEvent> sink)`; SSE types `meta`, `message`, `recommendations`, `citations`, `done`, `error`.

- [x] **Step 1: Write RED fallback and isolation tests**

```java
@Test
void missingModelUsesFallbackAndStillReturnsRoomsAndKnowledge() {
    ChatCapture capture = service.chat(userId, new ChatRequestVo("conv-1", "预算2500并说明押金"));
    assertThat(capture.event("meta").payload()).extracting("mode").isEqualTo("FALLBACK");
    assertThat(capture.event("recommendations").payload()).asList().isNotEmpty();
    assertThat(capture.event("citations").payload()).asList().isNotEmpty();
}

@Test
void sameConversationIdUsesDifferentRedisKeysForDifferentUsers() {
    assertThat(service.historyKey(1L, "same")).isNotEqualTo(service.historyKey(2L, "same"));
}
```

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=FallbackRentalChatEngineTest,RentalChatServiceImplTest test
```

- [x] **Step 3: Extract existing model logic without changing its behavior**

Move current `VectorStore.similaritySearch`、prompt assembly、`ChatClient.stream()` and citations into `ModelRentalChatEngine`. Keep `RoomSearchTool` registered in `ChatClientConfiguration`. Make model/vector beans conditional on nonblank AI keys and PG URL.

- [x] **Step 4: Implement fallback**

Fallback parses min/max rent and city/district keywords, calls the existing MySQL room query path, retrieves deposit/payment/appointment/repair/checkout sections from `docs/ai-rental-agent/rag-knowledge.md`, and emits the same structured events. It never creates appointments.

`RentalChatServiceImpl` chooses model when available, catches provider/vector errors, emits `mode=FALLBACK`, and retries through fallback. Redis key format is `ai:chat:history:{userId}:{conversationId}`; client-supplied IDs are length/character validated.

- [x] **Step 5: Verify GREEN and existing model tests**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=FallbackRentalChatEngineTest,RentalChatServiceImplTest test
.\mvnw.cmd -pl web/web-app -am -DskipTests package
```

- [x] **Step 6: Commit**

```powershell
git add common/src/main/java/com/atguigu/lease/config/ai/PgVectorDataSourceConfiguration.java web/web-app/src/main/java/com/atguigu/lease/web/app/config/ai web/web-app/src/main/java/com/atguigu/lease/web/app/service/ai web/web-app/src/main/java/com/atguigu/lease/web/app/tools/RoomSearchTool.java web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai
git commit -m "feat: add resilient rental chat fallback"
```

### Task 6: 实现预约草稿、二次确认、幂等写入和 Outbox

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
- Create: `model/src/main/java/com/atguigu/lease/model/entity/AppointmentEventOutbox.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/mapper/AiAppointmentIdempotencyMapper.java`
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/mapper/AppointmentEventOutboxMapper.java`
- Create: `web/web-app/src/main/resources/db/migration/V4__appointment_room_link.sql`
- Modify: `model/src/main/java/com/atguigu/lease/model/entity/ViewAppointment.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/mapper/RoomInfoMapper.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/mapper/ViewAppointmentMapper.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/appointment/AppointmentDraftServiceTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/appointment/AppointmentConfirmationServiceIT.java`

**Interfaces:**
- Produces: `POST /app/ai/appointments/draft`; `POST /app/ai/appointments/confirm`; Outbox row in same transaction as appointment.

- [x] **Step 1: Write RED validation and concurrency tests**

```java
@Test
void concurrentConfirmationCreatesOneAppointmentAndOneOutboxEvent() {
    String token = drafts.create(userId, validDraft).confirmationToken();
    List<AppointmentConfirmResponse> results = runConcurrently(2, () -> confirmations.confirm(userId, token));
    assertThat(appointmentCount(userId, validDraft.roomId())).isEqualTo(1);
    assertThat(outboxCount(results.getFirst().appointmentId())).isEqualTo(1);
    assertThat(results).extracting(AppointmentConfirmResponse::appointmentId)
        .containsOnly(results.getFirst().appointmentId());
}
```

Add tests for expired token, wrong user, past time, unavailable room, invalid phone and duplicate active appointment.

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AppointmentDraftServiceTest,AppointmentConfirmationServiceIT test
```

- [x] **Step 3: Implement two-phase mutation boundary**

Drafts are immutable Redis JSON under a random 256-bit token, TTL 10 minutes, namespaced by user. The durable table stores only SHA-256 token hash. Confirmation uses a Redis `SET NX` short claim, revalidates room/time while locking the room row, and in one `TransactionTemplate` transaction inserts `view_appointment`, idempotency and `appointment_event_outbox`. Duplicate-key races load the existing appointment and set `idempotentReplay=true`.

- [ ] **Step 4: Verify GREEN**

2026-08-05 non-Docker verification: 24 unit/profile tests GREEN, local Flyway V4 migration GREEN, package GREEN. `AppointmentConfirmationServiceIT` compiles and is skipped because Docker is unavailable; keep this step open until its concurrent container test runs GREEN.

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AppointmentDraftServiceTest,AppointmentConfirmationServiceIT test
```

- [x] **Step 5: Commit**

```powershell
git add model/src/main/java/com/atguigu/lease/model/entity/AiAppointmentIdempotency.java model/src/main/java/com/atguigu/lease/model/entity/AppointmentEventOutbox.java web/web-app/src/main/java/com/atguigu/lease/web/app/controller/ai web/web-app/src/main/java/com/atguigu/lease/web/app/mapper web/web-app/src/main/java/com/atguigu/lease/web/app/service web/web-app/src/main/java/com/atguigu/lease/web/app/vo/ai/appointment web/web-app/src/test/java/com/atguigu/lease/web/app/service/ai/appointment
git commit -m "feat: add confirmed AI appointment workflow"
```

### Task 7: 可靠投递预约事件并验证 RabbitMQ 恢复

**Files:**
- Create: `common/src/main/java/com/atguigu/lease/outbox/AppointmentOutboxPublisher.java`
- Create: `common/src/main/java/com/atguigu/lease/outbox/AppointmentOutboxRepository.java`
- Create: `common/src/main/java/com/atguigu/lease/outbox/OutboxPublishScheduler.java`
- Create: `common/src/main/java/com/atguigu/lease/outbox/RabbitAppointmentEventSender.java`
- Create: `common/src/main/java/com/atguigu/lease/consumer/appointment/AppointmentEventDeduplicator.java`
- Modify: `common/src/main/java/com/atguigu/lease/config/RabbitMQConfig.java`
- Modify: `common/src/main/java/com/atguigu/lease/consumer/appointment/AppointmentMessageConsumer.java`
- Modify: `common/src/main/java/com/atguigu/lease/message/appointment/AppointmentMessage.java`
- Modify: `web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/ViewAppointmentServiceImpl.java`
- Modify: `web/web-app/src/main/resources/application-default.yml`
- Modify: `web/web-app/src/main/resources/application-docker.yml`
- Test: `web/web-app/src/test/java/com/atguigu/lease/outbox/AppointmentOutboxPublisherTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/consumer/appointment/AppointmentMessageConsumerTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/web/app/service/impl/ViewAppointmentServiceImplTest.java`
- Test: `web/web-app/src/test/java/com/atguigu/lease/outbox/AppointmentOutboxPublisherIT.java`

**Interfaces:**
- Consumes: pending `appointment_event_outbox` rows.
- Produces: publisher-confirmed states `PENDING`, `PUBLISHED`, `DEAD`; at most five attempts.

- [ ] **Step 1: Write and run RED recovery test**

2026-08-05: recovery IT is written and compiles, but is skipped because Docker is unavailable. RED unit tests for publish retry/dead state, consumer deduplication/rejection, and transactional legacy appointment Outbox all ran as expected.

```java
@Test
void pendingEventPublishesAfterBrokerRecovery() {
    long eventId = insertPendingEvent();
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

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AppointmentOutboxPublisherIT test
```

- [x] **Step 2: Implement claim, confirms and backoff**

Claim with `FOR UPDATE SKIP LOCKED`, publish event ID as correlation ID, mark `PUBLISHED` only after confirm, and persist `next_attempt_at` with exponential backoff. Consumer deduplicates by event ID. Existing direct appointment publisher is replaced with a transactional Outbox write after its unit behavior is GREEN; container recovery verification remains open.

- [ ] **Step 3: Verify GREEN**

2026-08-05 non-Docker verification: 31 tests GREEN and package GREEN. `AppointmentOutboxPublisherIT` is skipped until Docker is available.

```powershell
.\mvnw.cmd -pl web/web-app -Dtest=AppointmentOutboxPublisherIT test
```

- [x] **Step 4: Commit**

```powershell
git add common/src/main/java/com/atguigu/lease/outbox common/src/main/java/com/atguigu/lease/config/RabbitMQConfig.java common/src/main/java/com/atguigu/lease/consumer/appointment/AppointmentMessageConsumer.java web/web-app/src/test/java/com/atguigu/lease/outbox web/web-app/src/main/java/com/atguigu/lease/web/app/service/impl/ViewAppointmentServiceImpl.java
git commit -m "feat: publish appointments through outbox"
```

### Task 8: 纳入 H5 并实现 SSE 对话与预约确认 UI

**Files:**
- Create: `frontend/rent-house-h5/**` copied from `E:\frontend\rentHouseH5\rentHouseH5` excluding `node_modules`, build output and local env files
- Create: `frontend/rent-house-h5/src/api/ai/index.ts`
- Create: `frontend/rent-house-h5/src/api/ai/types.ts`
- Create: `frontend/rent-house-h5/src/views/aiAssistant/aiAssistant.vue`
- Create: `frontend/rent-house-h5/src/components/AiRoomCard/AiRoomCard.vue`
- Create: `frontend/rent-house-h5/src/components/AiCitationList/AiCitationList.vue`
- Create: `frontend/rent-house-h5/src/components/AppointmentDraftSheet/AppointmentDraftSheet.vue`
- Create: `frontend/rent-house-h5/vitest.config.ts`
- Create: `frontend/rent-house-h5/playwright.config.ts`
- Create: `frontend/rent-house-h5/Dockerfile`
- Create: `frontend/rent-house-h5/nginx.conf`
- Modify: `frontend/rent-house-h5/package.json`
- Modify: `frontend/rent-house-h5/src/router/otherRoutes.ts`
- Modify: `frontend/rent-house-h5/src/views/message/message.vue`
- Modify: `compose.yaml`
- Test: `frontend/rent-house-h5/src/views/aiAssistant/aiAssistant.spec.ts`
- Test: `frontend/rent-house-h5/src/components/AppointmentDraftSheet/AppointmentDraftSheet.spec.ts`

**Interfaces:**
- Consumes: SSE event contract from Task 5 and appointment APIs from Task 6.
- Produces: `/ai-assistant`; room-detail navigation; explicit confirm; `/myAppointment` result navigation.

- [ ] **Step 1: Copy clean source and add test baseline**

Use the source manifest from `rg --files`; do not copy `node_modules`, `dist`, `.env.development`, `.env.production` or Git metadata. Preserve `LICENSE`.

- [ ] **Step 2: Write RED tests**

```ts
it('renders streaming recommendations and citations', async () => {
  const wrapper = mount(AiAssistant, { global: testPlugins })
  fakeSse.emit({ type: 'meta', payload: { mode: 'FALLBACK' } })
  fakeSse.emit({ type: 'recommendations', payload: [room] })
  fakeSse.emit({ type: 'citations', payload: [citation] })
  expect(wrapper.text()).toContain('降级模式')
  expect(wrapper.text()).toContain(room.apartmentName)
  expect(wrapper.text()).toContain(citation.source)
})

it('does not confirm before explicit click', async () => {
  const wrapper = mount(AppointmentDraftSheet, { props: { room, open: true } })
  await wrapper.find('[data-test="create-draft"]').trigger('click')
  expect(confirmAppointment).not.toHaveBeenCalled()
  await wrapper.find('[data-test="confirm-appointment"]').trigger('click')
  expect(confirmAppointment).toHaveBeenCalledTimes(1)
})
```

Run `npm run test:unit`; expected RED because components and test config do not exist.

- [ ] **Step 3: Implement UI and SSE parser**

Use `fetch` stream parsing for POST SSE because native `EventSource` cannot send the authenticated POST body. Reuse the existing Axios token source and send `access-token`. Preserve partial answer on reconnect errors, disable duplicate submits, use Vant action sheet for draft and explicit confirmation, and keep stable mobile dimensions.

- [ ] **Step 4: Verify GREEN**

```powershell
Set-Location frontend/rent-house-h5
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
git commit -m "feat: integrate H5 AI rental assistant"
```

### Task 9: 完成 fallback/model、预约和浏览器端到端验收

**Files:**
- Create: `web/web-app/src/test/java/com/atguigu/lease/e2e/RentalAgentClosedLoopIT.java`
- Create: `frontend/rent-house-h5/e2e/rental-agent-closed-loop.spec.ts`
- Create: `scripts/smoke-ai-agent.ps1`
- Modify: `readme.md`
- Modify: `docs/ai-rental-agent/api.md`
- Modify: `docs/ai-rental-agent/test-report.md`
- Modify: `.superpowers/sdd/progress.md`

**Interfaces:**
- Produces: one-command demo and fresh verification evidence for every spec acceptance criterion.

- [ ] **Step 1: Write RED API journey**

```java
@Test
void fallbackChatToConfirmedAppointmentIsClosedLoop() {
    String token = login("13800000000", "888888");
    SseCapture chat = chat(token, "预算2500并说明押金怎么退");
    assertThat(chat.meta().mode()).isEqualTo("FALLBACK");
    assertThat(chat.recommendations()).isNotEmpty();
    DraftResponse draft = draft(token, chat.recommendations().getFirst().roomId(), tomorrowAt(14, 0));
    ConfirmResponse first = confirm(token, draft.confirmationToken());
    ConfirmResponse replay = confirm(token, draft.confirmationToken());
    assertThat(replay.appointmentId()).isEqualTo(first.appointmentId());
    assertThat(listAppointments(token)).extracting("id").contains(first.appointmentId());
}
```

- [ ] **Step 2: Write RED Playwright journey**

Use `390x844` and desktop `1440x900`: demo login, send mixed question, wait for room/citation, create draft, confirm, open “我的预约”, assert the new appointment ID, and capture both pages.

- [ ] **Step 3: Add API smoke script and exact README commands**

```powershell
Copy-Item .env.example .env
docker compose up --build
.\scripts\verify-compose.ps1
.\scripts\smoke-ai-agent.ps1
```

Document `docker compose down --volumes` as a destructive demo reset command, but do not execute it without a new explicit user request.

- [ ] **Step 4: Run full fresh verification**

```powershell
git branch --show-current
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

Then run optional model smoke with valid keys and assert SSE `meta.mode=MODEL`, `RoomSearchTool` output and citations.

- [ ] **Step 5: Audit secrets and unrelated changes**

```powershell
Set-Location ..\..
rg -n "api-key:\s*[^$<]|AI_(CHAT|EMBED)_API_KEY=.+|access-key-secret:\s*[^$<]" . -g '!target/**' -g '!node_modules/**' -g '!.env'
git diff --check
git status --short
```

Expected: no tracked secret; branch is `agentRag`; `.idea/misc.xml` remains unstaged.

- [ ] **Step 6: Commit final verification artifacts**

```powershell
git add web/web-app/src/test/java/com/atguigu/lease/e2e frontend/rent-house-h5/e2e scripts/smoke-ai-agent.ps1 readme.md docs/ai-rental-agent .superpowers/sdd/progress.md
git commit -m "test: verify agentRag rental closed loop"
```

## Plan Completion Gate

- Re-read `docs/superpowers/specs/2026-08-03-ai-rental-agent-closed-loop-design.md` and map all nine acceptance criteria to fresh output.
- Confirm the 2026-06-30 RAG tests remain GREEN and no duplicate AI implementation was introduced.
- Confirm all nine tasks have RED evidence, GREEN evidence and their own commit.
- Confirm every commit belongs to `agentRag`; `master` remains at `origin/master`.
- Confirm `.idea/misc.xml` is not staged or committed.
- Confirm no required verification process is still running.
