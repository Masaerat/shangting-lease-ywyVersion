# AI 选房对话助手 + 企业级 RAG Implementation Plan (Plan 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the enterprise-grade AI rental assistant: room-data + uploaded-document RAG over pgvector, a Spring AI `@Tool` for structured room filtering, and an SSE streaming chat endpoint with multi-turn history and citations.

**Architecture:** Spring AI 1.0 GA on the upgraded Boot 3.4 / JDK 21 base (Plan 1). GLM via OpenAI-compatible endpoint. **Shared AI beans in `common`** (auto-configured ChatModel/EmbeddingModel + a manually-built `PgVectorStore` on a second Postgres datasource, MySQL stays primary). Ingestion (room sync + document pipeline) in **web-admin**; chat consumption in **web-app**. v1 uses **manual retrieval** in `RentalChatService` (simpler, unit-testable, yields structured recommendations) rather than `QuestionAnswerAdvisor`.

**Tech Stack:** Spring AI 1.0.0 (`spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-pgvector`, `spring-ai-tika-document-reader`), PostgreSQL + pgvector, Spring WebFlux (for `Flux` streaming types on the servlet stack), MinIO, Redis, MyBatis-Plus.

## Global Constraints

- **Prerequisite:** Plan 1 complete (Boot 3.4.1 + JDK 21 verified compiling).
- **Spring AI version: exactly 1.0.0 GA.** Use GA artifact names only (`spring-ai-starter-*`), NOT pre-GA `*-spring-boot-starter`. `QuestionAnswerAdvisor` is `org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor` (builder, not constructor). `SearchRequest.builder().topK().similarityThreshold()` (no `withTopK`). `VectorStore.delete(Filter.Expression)` (no `DeleteRequest`). `PgVectorStore.builder(jdbcTemplate, embeddingModel)`; `initialize-schema` defaults **false** → must set true.
- **Model config is placeholders:** all of `AI_BASE_URL`, `AI_API_KEY`, `AI_CHAT_MODEL`, `AI_EMBED_MODEL`, `AI_EMBED_DIM`, `PG_*` are `${...}` placeholders the operator fills. Never hardcode keys.
- **Zero regression:** existing modules keep compiling and behaving. AI code lives in new packages + `common/config/ai`.
- **Conventions:** `@RestController` returns `Result<T>` (non-streaming); SSE returns `SseEmitter`; `@Autowired` field injection; VOs `@Data`+`@Schema`; entities in `model`; shared config in `common`.
- **Streaming needs WebFlux:** add `spring-boot-starter-webflux` to `web-app` so `reactor.core.publisher.Flux` resolves (Spring MVC + WebFlux coexist fine).
- **Build env (non-persistent shell):** prefix every `mvn` call with
  `export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"`
- **Runtime cannot be verified in this environment** (no services / GLM key / Postgres). Each task's "test" is a unit test with mocks (Mockito) where possible, plus `mvn compile`. Live SSE/RAG verification is a manual step for the operator (documented in Phase F).

---

## File Structure

**`model`**
- `model/entity/AiKnowledgeDoc.java` — document metadata entity (`@TableName("ai_knowledge_doc")`).

**`common`**
- `config/ai/RagProperties.java` — `@ConfigurationProperties("app.ai.rag")`.
- `config/ai/PgVectorDataSourceConfiguration.java` — Postgres `DataSource` + `JdbcTemplate` + `PgVectorStore` bean; excludes `PgVectorStoreAutoConfiguration`; conditional on `app.datasource.pg.url`.
- `constant/AiRedisConstant.java` — chat-history key prefix/TTL.
- `pom.xml` — add Spring AI deps + postgres driver (shared by both apps).

**`web-admin`**
- `mapper/AiKnowledgeDocMapper.java` — MyBatis-Plus `BaseMapper`.
- `service/ai/RoomKnowledgeService.java` + `impl/RoomKnowledgeServiceImpl.java` — room → vector sync.
- `service/ai/DocumentKnowledgeService.java` + `impl/DocumentKnowledgeServiceImpl.java` — upload → parse → split → embed pipeline.
- `service/ai/KnowledgeManagementService.java` + `impl/KnowledgeManagementServiceImpl.java` — doc CRUD + delete vectors + reindex.
- `controller/ai/KnowledgeController.java` — admin endpoints.
- `vo/ai/KnowledgeDocVo.java`, `KnowledgeDocQueryVo.java`, `UploadResultVo.java`.
- `resources/mapper/AiKnowledgeDocMapper.xml` (only if custom SQL needed; BaseMapper usually suffices).
- `AdminWebApplication.java` — ensure `@EnableAsync`.
- `service/impl/RoomInfoServiceImpl.java` — publish room-change events.

**`web-app`**
- `pom.xml` — add `spring-boot-starter-webflux`.
- `tools/RoomSearchTool.java` — `@Tool` structured filter.
- `config/ai/ChatClientConfiguration.java` — `ChatClient` bean (system prompt + `defaultTools`).
- `service/ai/RentalChatService.java` + `impl/RentalChatServiceImpl.java` — SSE orchestration + manual retrieval + Redis history.
- `controller/ai/AiChatController.java` — `POST /app/ai/chat` SSE.
- `vo/ai/ChatRequestVo.java`, `ChatSseEvent.java`, `RoomCitationVo.java`.

---

## Task 1: Spring AI dependencies (root BOM + common + web-app webflux)

**Files:**
- Modify: `pom.xml` (root), `common/pom.xml`, `web/web-app/pom.xml`

**Interfaces:**
- Produces: `spring-ai-bom` 1.0.0 in root `<dependencyManagement>`; Spring AI starters in `common` (inherited by both apps); `spring-boot-starter-webflux` in `web-app`; `org.postgresql:postgresql` in `common`.

- [ ] **Step 1: Add Spring AI BOM to root `dependencyManagement`**

In root `pom.xml`, inside `<dependencyManagement><dependencies>`, add:
```xml
            <!-- Spring AI 1.0 GA BOM -->
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.0.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
```

- [ ] **Step 2: Add Spring AI deps + postgres driver to `common/pom.xml` `<dependencies>`**

Append:
```xml
        <!-- Spring AI: OpenAI 兼容(chat + embedding) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>
        <!-- Spring AI: pgvector 向量库 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
        </dependency>
        <!-- Spring AI: Tika 多格式文档解析 -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-tika-document-reader</artifactId>
        </dependency>
        <!-- PostgreSQL 驱动(向量库第二数据源) -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>
```

- [ ] **Step 3: Add WebFlux to `web/web-app/pom.xml` (for `Flux` streaming types)**

Append to `web/web-app/pom.xml` `<dependencies>`:
```xml
        <!-- Spring AI ChatClient.stream() 依赖响应式类型 Flux,Servlet 栈需引入 webflux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
```

- [ ] **Step 4: Verify it resolves + compiles (deps download on first run)**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q dependency:resolve -pl common
mvn -q compile
```
Expected: BUILD SUCCESS. If `spring-ai-*` artifacts 404, confirm the BOM version `1.0.0` and that Maven Central is reachable (add an aliyun mirror if needed).

- [ ] **Step 5: Commit**

```bash
git add pom.xml common/pom.xml web/web-app/pom.xml
git commit -m "build: 引入 Spring AI 1.0 GA(openai/pgvector/tika)+ postgres 驱动 + webflux"
```

---

## Task 2: `RagProperties` + `AiRedisConstant` (common)

**Files:**
- Create: `common/src/main/java/com/atguigu/lease/config/ai/RagProperties.java`
- Create: `common/src/main/java/com/atguigu/lease/common/constant/AiRedisConstant.java`

**Interfaces:**
- Produces: `RagProperties` (bean name `ragProperties`) with fields `chunkSize, chunkOverlap (minChunkSizeChars), minChunkLengthToEmbed, maxNumChunks, topK, similarityThreshold, historyTurns, namespaceDefault`; `AiRedisConstant.CHAT_HISTORY_PREFIX`, `CHAT_HISTORY_TTL_SEC`.

- [ ] **Step 1: Write `RagProperties`**

```java
package com.atguigu.lease.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.rag")
public class RagProperties {
    /** 分片目标 token 数 */
    private int chunkSize = 800;
    /** 分片最小字符数 */
    private int minChunkSizeChars = 350;
    /** 小于此长度的分片丢弃 */
    private int minChunkLengthToEmbed = 5;
    /** 单文档最大分片数 */
    private int maxNumChunks = 10000;
    /** 检索 top-k */
    private int topK = 5;
    /** 相似度阈值 */
    private double similarityThreshold = 0.75;
    /** 多轮历史保留轮数 */
    private int historyTurns = 10;
    /** 默认 namespace */
    private String namespaceDefault = "default";
}
```

- [ ] **Step 2: Write `AiRedisConstant`**

```java
package com.atguigu.lease.common.constant;

public class AiRedisConstant {
    /** 会话历史 key 前缀:ai:chat:history:{conversationId} */
    public static final String CHAT_HISTORY_PREFIX = "ai:chat:history:";
    public static final long CHAT_HISTORY_TTL_SEC = 2 * 60 * 60; // 2 小时
}
```

- [ ] **Step 3: Compile**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q -pl common -am compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add common/src/main/java/com/atguigu/lease/config/ai/RagProperties.java common/src/main/java/com/atguigu/lease/common/constant/AiRedisConstant.java
git commit -m "feat(common): RagProperties 与 AiRedisConstant"
```

---

## Task 3: `PgVectorDataSourceConfiguration` — second datasource + VectorStore (common)

**Files:**
- Create: `common/src/main/java/com/atguigu/lease/config/ai/PgVectorDataSourceConfiguration.java`
- Modify: both apps' `application-template.yml` (Spring AI + pg datasource block) — done in Task 16, but the bean must read `app.datasource.pg.*` and `spring.ai.vectorstore.pgvector.dimensions`.

**Interfaces:**
- Produces: beans `pgDataSource` (`DataSource`), `pgJdbcTemplate` (`JdbcTemplate`), and `vectorStore` (`org.springframework.ai.vectorstore.VectorStore` = `PgVectorStore`). Conditional on `app.datasource.pg.url` so the app still boots without Postgres configured.
- Note: ChatModel/EmbeddingModel are **auto-configured** by `spring-ai-starter-model-openai` — do NOT declare them.

- [ ] **Step 1: Write the configuration**

```java
package com.atguigu.lease.config.ai;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 向量库(PostgreSQL + pgvector)第二数据源与 VectorStore 配置。
 * - MySQL 仍为主数据源(Spring Boot 按 spring.datasource.* 自动配置,@Primary 由自动配置赋予)。
 * - 本类构造独立的 pg DataSource + JdbcTemplate,并手动构建 PgVectorStore,避免其抢占主数据源。
 * - 禁用 PgVectorStoreAutoConfiguration(见 application-template.yml 的 spring.autoconfigure.exclude)。
 * - 仅当配置了 app.datasource.pg.url 时生效,未配置时主业务仍可正常启动。
 */
@Configuration
@ConditionalOnProperty(name = "app.datasource.pg.url")
public class PgVectorDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("app.datasource.pg.hikari")
    public DataSource pgDataSource(
            @org.springframework.beans.factory.annotation.Value("${app.datasource.pg.url}") String url,
            @org.springframework.beans.factory.annotation.Value("${app.datasource.pg.username}") String username,
            @org.springframework.beans.factory.annotation.Value("${app.datasource.pg.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setPoolName("pg-vector-pool");
        return ds;
    }

    @Bean
    public JdbcTemplate pgJdbcTemplate(DataSource pgDataSource) {
        return new JdbcTemplate(pgDataSource);
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate pgJdbcTemplate, EmbeddingModel embeddingModel,
                                   RagProperties ragProperties,
                                   @org.springframework.beans.factory.annotation.Value("${spring.ai.vectorstore.pgvector.dimensions:1024}") int dimensions) throws Exception {
        return PgVectorStore.builder(pgJdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)   // GA 默认 false,必须显式开启以自动建表
                .build();
    }
}
```

- [ ] **Step 2: Exclude PgVectorStore auto-config (both apps)**

Add to both `application-template.yml` (full block in Task 16; the exclusion line is):
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.ai.vectorstore.pgvector.PgVectorStoreAutoConfiguration
```

- [ ] **Step 3: Compile**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q -pl common -am compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add common/src/main/java/com/atguigu/lease/config/ai/PgVectorDataSourceConfiguration.java
git commit -m "feat(common): pgvector 第二数据源 + 手动 PgVectorStore bean(条件装配)"
```

---

## Task 4: `AiKnowledgeDoc` entity + mapper (model / web-admin)

**Files:**
- Create: `model/src/main/java/com/atguigu/lease/model/entity/AiKnowledgeDoc.java`
- Create: `web/web-admin/src/main/java/com/atguigu/lease/web/admin/mapper/AiKnowledgeDocMapper.java`
- SQL: `db/ai-rental-agent/ai_knowledge_doc.sql`

**Interfaces:**
- Produces: `AiKnowledgeDoc` entity; `AiKnowledgeDocMapper extends BaseMapper<AiKnowledgeDoc>`.

- [ ] **Step 1: Write the entity**

```java
package com.atguigu.lease.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TableName("ai_knowledge_doc")
@Schema(description = "AI 知识库文档元数据")
public class AiKnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "原始文件名")
    private String docName;

    @Schema(description = "MIME 类型")
    private String contentType;

    @Schema(description = "文件大小(字节)")
    private Long sizeBytes;

    @Schema(description = "逻辑分区")
    private String namespace;

    @Schema(description = "状态:UPLOADING / INDEXED / FAILED")
    private String status;

    @Schema(description = "分片数")
    private Integer chunkCount;

    @Schema(description = "MinIO 对象 key")
    private String minioObjectKey;

    @Schema(description = "MinIO 桶名")
    private String minioBucket;

    @Schema(description = "失败原因")
    private String errorMessage;
}
```
(时间字段复用 `BaseEntity` 若其含 createTime/updateTime;否则按现有实体约定补字段。实现时确认 `BaseEntity` 字段后决定是否 extends。)

- [ ] **Step 2: Write the mapper**

```java
package com.atguigu.lease.web.admin.mapper;

import com.atguigu.lease.model.entity.AiKnowledgeDoc;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface AiKnowledgeDocMapper extends BaseMapper<AiKnowledgeDoc> {
}
```

- [ ] **Step 3: Write the DDL**

`db/ai-rental-agent/ai_knowledge_doc.sql`:
```sql
CREATE TABLE IF NOT EXISTS `ai_knowledge_doc` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `doc_name`         VARCHAR(255) NOT NULL,
  `content_type`     VARCHAR(128),
  `size_bytes`       BIGINT,
  `namespace`        VARCHAR(64)  NOT NULL DEFAULT 'default',
  `status`           VARCHAR(32)  NOT NULL,
  `chunk_count`      INT,
  `minio_object_key` VARCHAR(255),
  `minio_bucket`     VARCHAR(128),
  `error_message`    VARCHAR(512),
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_namespace_status` (`namespace`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识库文档元数据';
```

- [ ] **Step 4: Compile**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add model/src/main/java/com/atguigu/lease/model/entity/AiKnowledgeDoc.java web/web-admin/src/main/java/com/atguigu/lease/web/admin/mapper/AiKnowledgeDocMapper.java db/ai-rental-agent/ai_knowledge_doc.sql
git commit -m "feat(model/admin): AiKnowledgeDoc 实体 + Mapper + DDL"
```

---

## Task 5: `RoomKnowledgeService` — room → vector sync (web-admin)

**Files:**
- Create: `web/web-admin/src/main/java/com/atguigu/lease/web/admin/service/ai/RoomKnowledgeService.java`
- Create: `web/web-admin/src/main/java/com/atguigu/lease/web/admin/service/ai/impl/RoomKnowledgeServiceImpl.java`
- Test: `web/web-admin/src/test/java/com/atguigu/lease/web/admin/service/ai/impl/RoomKnowledgeServiceImplTest.java`

**Interfaces:**
- Consumes: `VectorStore` (Task 3), `RagProperties` (Task 2), `RoomInfoService` + `ApartmentInfoService` + `LeaseTermService` (existing).
- Produces: `RoomKnowledgeService.toDocument(RoomInfo room)` → `Document`; `syncRoom(Long roomId)`; `reindexAll()`.

- [ ] **Step 1: Write the failing test (document building is pure, no Spring context)**

```java
package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.RoomInfo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.junit.jupiter.api.Assertions.*;

class RoomKnowledgeServiceImplTest {

    private final RoomKnowledgeServiceImpl service =
            new RoomKnowledgeServiceImpl(null, null, null, null, null);

    @Test
    void toDocument_containsKeyRoomFactsAndNamespaceRooms() {
        RoomInfo room = new RoomInfo();
        room.setId(100L);
        room.setRoomNumber("A-101");
        room.setRent(new java.math.BigDecimal("3500"));
        ApartmentInfo apt = new ApartmentInfo();
        apt.setId(7L);
        apt.setName("阳光公寓");

        Document doc = service.toDocument(room, apt);

        String text = doc.getText();
        assertTrue(text.contains("A-101"));
        assertTrue(text.contains("3500"));
        assertEquals("rooms", doc.getMetadata().get("namespace"));
        assertEquals(100L, doc.getMetadata().get("roomRef"));
    }
}
```

- [ ] **Step 2: Run test → fails (constructor/method missing)**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q -pl web/web-admin -am test -Dtest=RoomKnowledgeServiceImplTest
```
Expected: FAIL (class/constructor not found).

- [ ] **Step 3: Implement**

```java
package com.atguigu.lease.web.admin.service.ai;

import org.springframework.ai.document.Document;
import java.util.List;

public interface RoomKnowledgeService {
    /** 全量重建房源向量 */
    void reindexAll();
    /** 同步单个房源(删旧 + 重新入库) */
    void syncRoom(Long roomId);
}
```

```java
package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.web.admin.service.ApartmentInfoService;
import com.atguigu.lease.web.admin.service.RoomInfoService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RoomKnowledgeServiceImpl implements RoomKnowledgeService {

    @Autowired private VectorStore vectorStore;
    @Autowired private RoomInfoService roomInfoService;
    @Autowired private ApartmentInfoService apartmentInfoService;
    @Autowired private RagProperties ragProperties;
    // LeaseTermService 可选:用于补充租期价格,构造时按需注入(测试用 null)
    @Autowired(required = false)
    private com.atguigu.lease.web.admin.service.LeaseTermService leaseTermService;

    public RoomKnowledgeServiceImpl(VectorStore vectorStore, RoomInfoService roomInfoService,
                                    ApartmentInfoService apartmentInfoService,
                                    com.atguigu.lease.web.admin.service.LeaseTermService leaseTermService,
                                    RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.roomInfoService = roomInfoService;
        this.apartmentInfoService = apartmentInfoService;
        this.leaseTermService = leaseTermService;
        this.ragProperties = ragProperties;
    }

    /** 构造房源描述文档(纯函数,便于单测)。 */
    public Document toDocument(RoomInfo room, ApartmentInfo apartment) {
        StringBuilder sb = new StringBuilder();
        if (apartment != null && apartment.getName() != null) sb.append("公寓:").append(apartment.getName()).append("。");
        sb.append("房间号:").append(room.getRoomNumber()).append("。");
        if (room.getRent() != null) sb.append("月租金:").append(room.getRent()).append("元。");
        // 面积/朝向/标签等按 RoomInfo 实际字段补充(实现时对照实体补全)
        Map<String, Object> meta = Map.of(
                "namespace", "rooms",
                "docType", "room",
                "roomRef", room.getId(),
                "source", apartment == null ? "" : apartment.getName());
        return new Document(sb.toString(), meta);
    }

    @Override
    public void syncRoom(Long roomId) {
        RoomInfo room = roomInfoService.getById(roomId);
        if (room == null) return;
        // 先删该 roomRef 的旧向量
        Expression del = new FilterExpressionBuilder().eq("roomRef", String.valueOf(roomId)).build();
        vectorStore.delete(del);
        ApartmentInfo apt = room.getApartmentId() == null ? null : apartmentInfoService.getById(room.getApartmentId());
        vectorStore.add(List.of(toDocument(room, apt)));
    }

    @Override
    public void reindexAll() {
        // 删整个 rooms namespace 再全量灌入(简单可靠;大数据量可改为分批)
        Expression del = new FilterExpressionBuilder().eq("namespace", "rooms").build();
        vectorStore.delete(del);
        List<RoomInfo> rooms = roomInfoService.list();
        List<Document> docs = rooms.stream().map(r -> {
            ApartmentInfo apt = r.getApartmentId() == null ? null : apartmentInfoService.getById(r.getApartmentId());
            return toDocument(r, apt);
        }).toList();
        // 分批 add,避免单次过大
        int batch = 100;
        for (int i = 0; i < docs.size(); i += batch) {
            vectorStore.add(docs.subList(i, Math.min(i + batch, docs.size())));
        }
    }
}
```
> Implementer note: confirm `RoomInfo` field names (`roomNumber`, `rent`, `apartmentId`) match the real entity; enrich the text in `toDocument` with area/orientation/labels using actual getters.

- [ ] **Step 4: Run test → passes**

```bash
mvn -q -pl web/web-admin -am test -Dtest=RoomKnowledgeServiceImplTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/web-admin/src/main/java/com/atguigu/lease/web/admin/service/ai/ web/web-admin/src/test/java/com/atguigu/lease/web/admin/service/ai/
git commit -m "feat(admin): RoomKnowledgeService 房源向量化同步"
```

---

## Task 6: Room change event + async listener (web-admin)

**Files:**
- Modify: `web/web-admin/src/main/java/com/atguigu/lease/web/admin/service/impl/RoomInfoServiceImpl.java`
- Create: `web/web-admin/src/main/java/com/atguigu/lease/web/admin/service/ai/event/RoomChangedEvent.java`
- Create: listener inside `RoomKnowledgeServiceImpl` (or a new `RoomKnowledgeEventListener`)
- Modify: `web/web-admin/.../AdminWebApplication.java` — add `@EnableAsync`

**Interfaces:**
- Produces: `RoomChangedEvent(roomId, action)`; `RoomInfoServiceImpl` publishes it on save/update/delete; `@Async @EventListener` calls `roomKnowledgeService.syncRoom(roomId)`.

- [ ] **Step 1: Confirm `@EnableAsync` on `AdminWebApplication`** (read the file; if missing, add `@EnableAsync` next to `@SpringBootApplication`).

- [ ] **Step 2: Create event + publisher + listener**

`RoomChangedEvent.java`:
```java
package com.atguigu.lease.web.admin.service.ai.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RoomChangedEvent extends ApplicationEvent {
    private final Long roomId;
    public RoomChangedEvent(Object source, Long roomId) {
        super(source);
        this.roomId = roomId;
    }
}
```

In `RoomInfoServiceImpl`, publish after save/update/remove (use the injected `ApplicationEventPublisher`):
```java
@Autowired private org.springframework.context.ApplicationEventPublisher eventPublisher;
// 在保存/更新成功后:
eventPublisher.publishEvent(new RoomChangedEvent(this, room.getId()));
// 在删除成功后(若有 room id):
eventPublisher.publishEvent(new RoomChangedEvent(this, roomId));
```

Add an async listener (new class `RoomKnowledgeEventListener`):
```java
package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.web.admin.service.ai.RoomKnowledgeService;
import com.atguigu.lease.web.admin.service.ai.event.RoomChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RoomKnowledgeEventListener {
    @Autowired private RoomKnowledgeService roomKnowledgeService;

    @Async
    @EventListener
    public void onRoomChanged(RoomChangedEvent event) {
        roomKnowledgeService.syncRoom(event.getRoomId());
    }
}
```

- [ ] **Step 3: Compile**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q compile
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(admin): 房源变更事件 + 异步向量同步监听"
```

---

## Task 7: `DocumentKnowledgeService` — upload → parse → split → embed (web-admin)

**Files:**
- Create: `service/ai/DocumentKnowledgeService.java` + `impl/DocumentKnowledgeServiceImpl.java`
- Test: `impl/DocumentKnowledgeServiceImplTest.java`

**Interfaces:**
- Consumes: `VectorStore`, `RagProperties`, `AiKnowledgeDocMapper`, `MinioClient` + `MinioProperties` (existing in common).
- Produces: `DocumentKnowledgeService.ingest(Long docId)` (async) and `uploadAndIngest(MultipartFile file, String namespace) -> Long docId`.

- [ ] **Step 1: Write the failing test (splitter param wiring, mock VectorStore + mapper)**

```java
package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.config.ai.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentKnowledgeServiceImplTest {

    private final RagProperties props = new RagProperties(); // 默认值
    private final DocumentKnowledgeServiceImpl svc =
            new DocumentKnowledgeServiceImpl(null, null, null, null, props);

    @Test
    void buildSplitter_usesRagProperties() {
        TokenTextSplitter s = svc.buildSplitter();
        // 默认 chunkSize=800;split 后段数应 > 1(给一段长文本)
        String big = "A".repeat(5000);
        Document d = new Document(big);
        List<Document> chunks = s.split(List.of(d));
        assertTrue(chunks.size() > 1);
    }
}
```

- [ ] **Step 2: Run → fails (class/method missing)**

- [ ] **Step 3: Implement**

```java
package com.atguigu.lease.web.admin.service.ai;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentKnowledgeService {
    /** 上传到 MinIO 并落元数据,返回 docId;随后异步解析入库 */
    Long uploadAndIngest(MultipartFile file, String namespace);
    /** 异步:解析已有 docId 的文档 → 分片 → 向量化 → 更新状态 */
    void ingest(Long docId);
}
```

```java
package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.common.minio.MinioProperties;
import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.model.entity.AiKnowledgeDoc;
import com.atguigu.lease.web.admin.mapper.AiKnowledgeDocMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentKnowledgeServiceImpl implements DocumentKnowledgeService {

    private static final String STATUS_INDEXED = "INDEXED";
    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_FAILED = "FAILED";

    @Autowired private VectorStore vectorStore;
    @Autowired private AiKnowledgeDocMapper docMapper;
    @Autowired private MinioClient minioClient;
    @Autowired private MinioProperties minioProperties;
    @Autowired private RagProperties ragProperties;

    public DocumentKnowledgeServiceImpl(VectorStore vectorStore, AiKnowledgeDocMapper docMapper,
                                        MinioClient minioClient, MinioProperties minioProperties,
                                        RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.docMapper = docMapper;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.ragProperties = ragProperties;
    }

    /** 暴露给单测:按 RagProperties 构造分片器。 */
    public TokenTextSplitter buildSplitter() {
        return new TokenTextSplitter(
                ragProperties.getChunkSize(),
                ragProperties.getMinChunkSizeChars(),
                ragProperties.getMinChunkLengthToEmbed(),
                ragProperties.getMaxNumChunks(),
                true);
    }

    @Override
    public Long uploadAndIngest(MultipartFile file, String namespace) {
        try {
            String objectKey = "ai-doc/" + UUID.randomUUID() + "/" + file.getOriginalFilename();
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectKey)
                        .stream(in, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            AiKnowledgeDoc doc = new AiKnowledgeDoc()
                    .setDocName(file.getOriginalFilename())
                    .setContentType(file.getContentType())
                    .setSizeBytes(file.getSize())
                    .setNamespace(namespace == null ? ragProperties.getNamespaceDefault() : namespace)
                    .setStatus(STATUS_UPLOADING)
                    .setMinioObjectKey(objectKey)
                    .setMinioBucket(minioProperties.getBucketName());
            docMapper.insert(doc);
            ingest(doc.getId());
            return doc.getId();
        } catch (Exception e) {
            throw new RuntimeException("文档上传失败: " + e.getMessage(), e);
        }
    }

    @Async
    @Override
    public void ingest(Long docId) {
        AiKnowledgeDoc doc = docMapper.selectById(docId);
        try {
            try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(doc.getMinioBucket()).object(doc.getMinioObjectKey()).build())) {
                byte[] bytes = in.readAllBytes();
                List<Document> raw = new TikaDocumentReader(new ByteArrayResource(bytes)).get();
                Map<String, Object> shared = Map.of(
                        "namespace", doc.getNamespace(),
                        "docType", "doc",
                        "docId", docId,
                        "source", doc.getDocName());
                List<Document> enriched = raw.stream()
                        .map(d -> new Document(d.getText(),
                                java.util.stream.Stream.concat(
                                                d.getMetadata().entrySet().stream(),
                                                shared.entrySet().stream())
                                        .collect(java.util.stream.Collectors.toMap(
                                                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b))))
                        .toList();
                List<Document> chunks = buildSplitter().split(enriched);
                vectorStore.add(chunks);
                doc.setChunkCount(chunks.size());
                doc.setStatus(STATUS_INDEXED);
                doc.setErrorMessage(null);
            }
        } catch (Exception e) {
            doc.setStatus(STATUS_FAILED);
            doc.setErrorMessage(e.getMessage());
        }
        docMapper.updateById(doc);
    }
}
```

- [ ] **Step 4: Run test → passes**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(admin): DocumentKnowledgeService 文档上传→解析→分片→向量化管线"
```

---

## Task 8: `KnowledgeManagementService` + `KnowledgeController` (web-admin)

**Files:**
- Create: `service/ai/KnowledgeManagementService.java` + impl
- Create: `controller/ai/KnowledgeController.java`
- Create: `vo/ai/KnowledgeDocVo.java`, `KnowledgeDocQueryVo.java`, `UploadResultVo.java`

**Interfaces:**
- Produces: endpoints `POST /admin/ai/docs` (upload), `GET /admin/ai/docs/page` (list), `DELETE /admin/ai/docs/{id}` (delete doc + MinIO + vectors), `POST /admin/ai/docs/reindex` (reindex namespace).

- [ ] **Step 1: VOs**

`UploadResultVo` (`@Data` + `@Schema`): fields `Long docId`, `String status`.
`KnowledgeDocVo`: `Long id, String docName, String contentType, Long sizeBytes, String namespace, String status, Integer chunkCount`.
`KnowledgeDocQueryVo`: `Integer current=1, Integer size=10, String namespace, String status, String keyword`.

- [ ] **Step 2: Service — delete by metadata filter**

```java
package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.model.entity.AiKnowledgeDoc;
import com.atguigu.lease.web.admin.mapper.AiKnowledgeDocMapper;
import com.atguigu.lease.web.admin.service.ai.RoomKnowledgeService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeManagementServiceImpl
        implements com.atguigu.lease.web.admin.service.ai.KnowledgeManagementService {

    @Autowired private AiKnowledgeDocMapper docMapper;
    @Autowired private VectorStore vectorStore;
    @Autowired private MinioClient minioClient;
    @Autowired private RoomKnowledgeService roomKnowledgeService;

    @Override
    public void deleteDoc(Long id) {
        AiKnowledgeDoc doc = docMapper.selectById(id);
        if (doc == null) return;
        // 删向量(按 docId 元数据过滤)
        vectorStore.delete(new FilterExpressionBuilder().eq("docId", String.valueOf(id)).build());
        // 删 MinIO 对象
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(doc.getMinioBucket()).object(doc.getMinioObjectKey()).build());
        } catch (Exception ignored) { /* 对象已不存在则忽略 */ }
        docMapper.deleteById(id);
    }

    @Override
    public void reindexNamespace(String namespace) {
        if ("rooms".equals(namespace)) {
            roomKnowledgeService.reindexAll();
        } else {
            // 文档:删该 namespace 全部向量,再逐个重新 ingest(从 MinIO 读回)
            vectorStore.delete(new FilterExpressionBuilder().eq("namespace", namespace).build());
            // 逐文档重灌略;实现时可注入 DocumentKnowledgeService 重新 ingest 该 namespace 下所有 doc
        }
    }
}
```
Define the interface `KnowledgeManagementService` with `deleteDoc(Long)`, `reindexNamespace(String)`, plus list/page delegates to mapper.

- [ ] **Step 3: Controller**

```java
package com.atguigu.lease.web.admin.controller.ai;

import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.web.admin.service.ai.DocumentKnowledgeService;
import com.atguigu.lease.web.admin.service.ai.KnowledgeManagementService;
import com.atguigu.lease.web.admin.vo.ai.UploadResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "后台-AI知识库")
@RestController
@RequestMapping("/admin/ai/docs")
public class KnowledgeController {

    @Autowired private DocumentKnowledgeService documentKnowledgeService;
    @Autowired private KnowledgeManagementService knowledgeManagementService;

    @Operation(summary = "上传文档并解析入库")
    @PostMapping
    public Result<UploadResultVo> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "namespace", required = false) String namespace) {
        Long docId = documentKnowledgeService.uploadAndIngest(file, namespace);
        return Result.ok(new UploadResultVo(docId, "INDEXED"));
    }

    @Operation(summary = "删除文档(同时删向量与MinIO对象)")
    @DeleteMapping("{id}")
    public Result delete(@PathVariable Long id) {
        knowledgeManagementService.deleteDoc(id);
        return Result.ok();
    }

    @Operation(summary = "按 namespace 重建索引")
    @PostMapping("reindex")
    public Result reindex(@RequestParam String namespace) {
        knowledgeManagementService.reindexNamespace(namespace);
        return Result.ok();
    }
}
```
(Add a `GET page` list endpoint using `AiKnowledgeDocMapper` + `Page` per existing controller conventions.)

- [ ] **Step 4: Compile**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q compile
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(admin): 知识库管理接口(上传/删除/重建索引)"
```

---

## Task 9: `RoomSearchTool` — `@Tool` structured filter (web-app)

**Files:**
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/tools/RoomSearchTool.java`

**Interfaces:**
- Consumes: `RoomInfoService` + `ApartmentInfoService` (existing in web-app).
- Produces: `RoomSearchTool.searchRooms(city, district, roomCount, maxMonthlyRent) -> List<RoomHit>` registered as a `@Tool`.

- [ ] **Step 1: Implement**

```java
package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.RoomInfo;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import com.atguigu.lease.web.app.service.RoomInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class RoomSearchTool {

    public record RoomHit(Long roomId, String apartment, String roomNumber, BigDecimal rent, Long apartmentId) {}

    @Autowired private RoomInfoService roomInfoService;
    @Autowired private ApartmentInfoService apartmentInfoService;

    @Tool(description = "按城市/区/户型/最高月租金精确筛选在租房源,返回候选房源列表(用于用户给出明确预算、位置、户型条件时)。所有参数均可为空。")
    public List<RoomHit> searchRooms(
            @ToolParam(required = false, description = "城市名,如 北京") String city,
            @ToolParam(required = false, description = "区,如 朝阳区") String district,
            @ToolParam(required = false, description = "卧室数/户型,如 2") Integer roomCount,
            @ToolParam(required = false, description = "最高月租金(元),如 3000") BigDecimal maxMonthlyRent) {

        LambdaQueryWrapper<RoomInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(RoomInfo::getIsRelease, 1); // 仅在售;字段名按实际实体确认
        if (maxMonthlyRent != null) qw.le(RoomInfo::getRent, maxMonthlyRent);
        // roomCount/district 需经 ApartmentInfo/属性关联过滤;实现时按真实字段补充
        List<RoomInfo> rooms = roomInfoService.list(qw);
        if (rooms.isEmpty()) return Collections.emptyList();
        return rooms.stream().limit(20).map(r -> {
            ApartmentInfo apt = r.getApartmentId() == null ? null : apartmentInfoService.getById(r.getApartmentId());
            return new RoomHit(r.getId(), apt == null ? null : apt.getName(), r.getRoomNumber(), r.getRent(), r.getApartmentId());
        }).toList();
    }
}
```
> Implementer note: confirm `RoomInfo.isRelease`/`rent`/`roomNumber` field names; the `roomCount`/`district` filtering is sketched — wire to actual location/attribute fields.

- [ ] **Step 2: Compile**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q -pl web/web-app -am compile
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(app): RoomSearchTool 结构化过滤工具(@Tool)"
```

---

## Task 10: `ChatClientConfiguration` (web-app)

**Files:**
- Create: `web/web-app/src/main/java/com/atguigu/lease/web/app/config/ai/ChatClientConfiguration.java`

**Interfaces:**
- Consumes: auto-configured `ChatClient.Builder`, `RoomSearchTool` (Task 9), `RagProperties`.
- Produces: a `ChatClient` bean with system prompt + `defaultTools(roomSearchTool)`.

- [ ] **Step 1: Implement**

```java
package com.atguigu.lease.web.app.config.ai;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.tools.RoomSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfiguration {

    @Bean
    public ChatClient rentalChatClient(ChatClient.Builder builder, RoomSearchTool roomSearchTool) {
        String system = """
                你是一名专业、友善的租房顾问。根据给定的房源资料回答用户问题、推荐房源。
                规则:
                1) 只依据提供的资料与 RoomSearchTool 的结果作答,不要编造不存在的房源。
                2) 推荐时给出房间号、租金与简短理由。
                3) 若用户给出明确的预算/位置/户型,优先调用 searchRooms 工具精确筛选。
                4) 不确定时如实告知。
                """;
        return builder.defaultSystem(system).defaultTools(roomSearchTool).build();
    }
}
```
> Note: retrieval context is injected per-request by `RentalChatService` (manual), so we do NOT register `QuestionAnswerAdvisor` in v1.

- [ ] **Step 2: Compile + commit**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q -pl web/web-app -am compile
git add -A
git commit -m "feat(app): ChatClient 配置(系统提示 + RoomSearchTool)"
```

---

## Task 11: Chat VOs (web-app)

**Files:**
- Create: `vo/ai/ChatRequestVo.java`, `ChatSseEvent.java`, `RoomCitationVo.java`

- [ ] **Step 1: Implement**

```java
// ChatRequestVo
@Data @Schema(description="AI 对话请求")
public class ChatRequestVo {
    @Schema(description="用户消息") private String message;
    @Schema(description="会话ID,为空则服务端按用户生成") private String conversationId;
}
// RoomCitationVo
@Data @Schema(description="推荐房源") @AllArgsConstructor @NoArgsConstructor
public class RoomCitationVo {
    private Long roomId; private String apartment; private String roomNumber; private BigDecimal rent; private String source;
}
// ChatSseEvent (流式事件载体)
@Data @AllArgsConstructor @NoArgsConstructor
public class ChatSseEvent {
    private String type;   // message | done | error
    private Object payload; // String(token) / List<RoomCitationVo> / String(errorMsg)
}
```
(Add package + imports; Lombok `@Data`, `@Schema` per conventions.)

- [ ] **Step 2: Compile + commit**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q -pl web/web-app -am compile
git add -A && git commit -m "feat(app): AI 对话 VO"
```

---

## Task 12: `RentalChatService` + `AiChatController` — SSE streaming (web-app)

**Files:**
- Create: `service/ai/RentalChatService.java` + `impl/RentalChatServiceImpl.java`
- Create: `controller/ai/AiChatController.java`
- Test: `service/ai/impl/RentalChatServiceImplTest.java` (mock ChatClient + VectorStore)

**Interfaces:**
- Consumes: `ChatClient` (Task 10), `VectorStore` (common), `RagProperties`, `CacheUtil` (Redis, common).
- Produces: `RentalChatService.chat(ChatRequestVo, SseEmitter)`; controller `POST /app/ai/chat` returns `SseEmitter`.

- [ ] **Step 1: Failing test — manual retrieval picks rooms & builds context**

```java
// 用 mock VectorStore.similaritySearch 返回带 namespace=rooms 的文档,断言 buildContext 含其文本,
// 且 toCitations 把 roomRef 转成 RoomCitationVo。覆盖:历史读取、上下文拼接、引用转换。
```
(Sketch: assert `service.toCitations(docs)` returns a list whose element `roomId` equals the doc metadata `roomRef`.)

- [ ] **Step 2: Implement service**

```java
package com.atguigu.lease.web.app.service.ai;

import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RentalChatService {
    void chat(ChatRequestVo request, SseEmitter emitter);
}
```

```java
package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.common.constant.AiRedisConstant;
import com.atguigu.lease.common.utils.CacheUtil;
import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import com.atguigu.lease.web.app.vo.ai.RoomCitationVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.Executor;

@Service
public class RentalChatServiceImpl implements com.atguigu.lease.web.app.service.ai.RentalChatService {

    @Autowired private ChatClient rentalChatClient;
    @Autowired private VectorStore vectorStore;
    @Autowired private RagProperties ragProperties;
    @Autowired private CacheUtil cacheUtil;
    @Autowired private Executor applicationTaskExecutor; // 虚拟线程池
    private final ObjectMapper om = new ObjectMapper();

    @Override
    public void chat(ChatRequestVo request, SseEmitter emitter) {
        applicationTaskExecutor.execute(() -> {
            try {
                String conversationId = request.getConversationId();
                if (conversationId == null || conversationId.isBlank())
                    conversationId = "u-" + currentUserId(); // 简化:无 conversationId 时按用户

                // 1. 取历史
                List<String> history = loadHistory(conversationId);

                // 2. 手动检索(混合来源)
                List<Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(request.getMessage())
                        .topK(ragProperties.getTopK())
                        .similarityThreshold(ragProperties.getSimilarityThreshold())
                        .build());

                // 3. 拼上下文 + 提问
                String context = buildContext(retrieved);
                String prompt = (context.isBlank() ? "" : "参考资料:\n" + context + "\n\n")
                        + String.join("\n", history) + "\n用户:" + request.getMessage();

                // 4. 流式回答
                StringBuilder answer = new StringBuilder();
                rentalChatClient.prompt().user(prompt).stream().content().subscribe(
                        token -> { answer.append(token); send(emitter, "message", token); },
                        err -> send(emitter, "error", err.getMessage()),
                        () -> { send(emitter, "done", toCitations(retrieved)); emitter.complete();
                                saveHistory(conversationId, request.getMessage(), answer.toString()); }
                );
            } catch (Exception e) {
                send(emitter, "error", e.getMessage());
                emitter.completeWithError(e);
            }
        });
    }

    /** 把检索文档转成上下文文本(纯函数,便于测试)。 */
    public String buildContext(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) sb.append("[").append(i + 1).append("] ")
                .append(docs.get(i).getText()).append("\n");
        return sb.toString();
    }

    /** 把 namespace=rooms 的检索结果转成引用(纯函数,便于测试)。 */
    public List<RoomCitationVo> toCitations(List<Document> docs) {
        if (docs == null) return List.of();
        List<RoomCitationVo> list = new ArrayList<>();
        for (Document d : docs) {
            if (!"rooms".equals(d.getMetadata().get("namespace"))) continue;
            Object ref = d.getMetadata().get("roomRef");
            list.add(new RoomCitationVo(
                    ref == null ? null : Long.valueOf(ref.toString()),
                    (String) d.getMetadata().get("source"),
                    null, null, "rooms"));
        }
        return list;
    }

    private void send(SseEmitter emitter, String type, Object payload) {
        try { emitter.send(SseEmitter.event().name("chat").data(new ChatSseEvent(type, payload))); }
        catch (Exception ignored) {}
    }

    private List<String> loadHistory(String cid) {
        // CacheUtil 提供 Redis 读写;返回最近 N 轮文本。实现时按 CacheUtil 实际方法调用。
        return List.of();
    }
    private void saveHistory(String cid, String user, String answer) {
        // 追加一轮并裁剪到 historyTurns,设置 TTL。
    }
    private Long currentUserId() { return 0L; } // 实现时用 LoginUserHolder.getLoginUser().getUserId()
}
```
> Implementer note: wire `CacheUtil` (read its actual API) for `loadHistory`/`saveHistory`, and `LoginUserHolder` for `currentUserId`. The two pure methods (`buildContext`, `toCitations`) are what the unit test covers.

- [ ] **Step 3: Controller**

```java
package com.atguigu.lease.web.app.controller.ai;

import com.atguigu.lease.web.app.service.ai.RentalChatService;
import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "APP-AI对话")
@RestController
@RequestMapping("/app/ai")
public class AiChatController {

    @Autowired private RentalChatService rentalChatService;

    @Operation(summary = "AI 选房对话(SSE 流式)")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequestVo request) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时(或按需设置)
        rentalChatService.chat(request, emitter);
        return emitter;
    }
}
```

- [ ] **Step 4: Run unit test → passes; compile**

```bash
export JAVA_HOME="/d/SoftWare/JDK/JDK21"; export PATH="$JAVA_HOME/bin:/d/SoftWare/Maven/apache-maven-3.9.16/bin:$PATH"
mvn -q -pl web/web-app -am test -Dtest=RentalChatServiceImplTest
mvn -q compile
```

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(app): AI 对话 SSE 流式服务 + 控制器(手动检索+多轮历史+引用)"
```

---

## Task 13: Auth — allow AI chat under existing interceptor

**Files:**
- Confirm `web/web-app/custom/config/WebMvcConfiguration.java` intercepts `/app/**` and excludes only `/app/login/**`.

- [ ] **Step 1:** `/app/ai/chat` is under `/app/**` → it IS intercepted (requires `access-token`). This is the intended behavior (chat is for logged-in users). **No change needed.** If the operator wants it open, add `/app/ai/**` to `excludePathPatterns` — but default is authed. Document this.

- [ ] **Step 2: Commit (none if no change)** — record the decision in the ledger.

---

## Task 14: Extend `application-template.yml` (both apps)

**Files:**
- Modify: `web/web-app/src/main/resources/application-template.yml`, `web/web-admin/src/main/resources/application-template.yml`

- [ ] **Step 1: Append the AI + pg datasource block to BOTH templates**

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.ai.vectorstore.pgvector.PgVectorStoreAutoConfiguration
  ai:
    openai:
      base-url: ${AI_BASE_URL}            # 如 https://open.bigmodel.cn/api/paas/v4
      api-key: ${AI_API_KEY}
      chat:
        options:
          model: ${AI_CHAT_MODEL}
      embedding:
        options:
          model: ${AI_EMBED_MODEL}
    vectorstore:
      pgvector:
        dimensions: ${AI_EMBED_DIM}        # 与 embedding 模型一致(GLM embedding-3 可配 1024/2048)

app:
  datasource:
    pg:                                    # 向量库 Postgres(第二数据源)
      url: ${PG_URL}
      username: ${PG_USER}
      password: ${PG_PASSWORD}
  ai:
    rag:
      chunk-size: 800
      chunk-overlap: 350
      top-k: 5
      similarity-threshold: 0.75
      history-turns: 10
      namespace-default: default
```

- [ ] **Step 2: Commit**

```bash
git add web/web-app/src/main/resources/application-template.yml web/web-admin/src/main/resources/application-template.yml
git commit -m "docs: 配置模板补充 Spring AI / pgvector / 第二数据源(占位符)"
```

---

## Task 15: Verification (manual, operator-run)

This plan's runtime cannot be verified in the dev environment (no services / GLM key / Postgres). The operator verifies after standing up services:

- [ ] **Compile gate (automated here):** `mvn clean compile` and `mvn clean package -DskipTests` across all modules — must be green.
- [ ] **Unit tests:** `RoomKnowledgeServiceImplTest`, `DocumentKnowledgeServiceImplTest`, `RentalChatServiceImplTest` green.
- [ ] **Operator steps (with services up + yml filled + GLM key):**
  1. Start Postgres with pgvector; set `PG_URL/USER/PASSWORD`, `AI_*`.
  2. `mvn -pl web/web-admin spring-boot:run` → upload a doc via `/admin/ai/docs` → confirm `ai_knowledge_doc.status=INDEXED`.
  3. Trigger room reindex → confirm `vector_store` has `namespace=rooms` rows.
  4. `mvn -pl web/web-app spring-boot:run` → `curl -N -X POST http://localhost:8081/app/app/ai/chat ...` (with `access-token`) → confirm SSE `message` chunks + final `done` with citations.
- [ ] Record results in the ledger.

---

## Self-Review (run after writing)

1. **Spec coverage:** spec §5.4 class placement — covered (common/config/ai, model entity, web-app chat+tool, web-admin ingestion+mgmt). spec §7 doc pipeline — Task 7. spec §8 chat/SSE/hybrid — Task 12 (manual retrieval v1). spec §9 config — Task 14. spec §10 errors — Task 12 in-stream error event. spec §11 tests — unit tests in Tasks 5/7/12.
2. **v1 deviation flagged:** manual retrieval replaces `QuestionAnswerAdvisor` (simpler, testable). Documented in Architecture + Task 10 note.
3. **Type consistency:** `RoomHit` (tool) vs `RoomCitationVo` (chat) are intentionally different (tool result for LLM vs UI citation). `ChatSseEvent{type,payload}` consistent across Task 11/12.
4. **Implementer-flagged uncertainties:** real entity field names (`RoomInfo.roomNumber/rent/apartmentId/isRelease`), `BaseEntity` time fields, `CacheUtil` API, `LeaseTermService` package — marked with "implementer note" since they need confirmation against the real (post-upgrade) code at execution time.
