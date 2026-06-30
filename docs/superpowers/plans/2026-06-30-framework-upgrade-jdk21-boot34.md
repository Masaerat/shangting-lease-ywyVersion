# Framework Upgrade (JDK 21 + Spring Boot 3.4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the whole multi-module lease app from JDK 17 + Spring Boot 3.0.5 to JDK 21 + Spring Boot 3.4.x with **zero behavioral regression**, as the prerequisite for the AI RAG feature (Spring AI 1.0 GA requires Boot 3.3+/3.4).

**Architecture:** Pure version migration — no functional changes. Bump parent + Java compiler target; align transitive deps that are known to break on Boot 3.4 (MyBatis-Plus starter artifact + version, knife4j version); remove the manual spring-amqp version override so Boot 3.4 manages it. Then compile-fix loop, then regression gate (build + existing tests + optional runtime smoke).

**Tech Stack:** JDK 21 (LTS), Spring Boot 3.4.1, MyBatis-Plus 3.5.9 (`mybatis-plus-spring-boot3-starter`), knife4j 4.5.0, Maven multi-module.

## Global Constraints

- **Zero regression (hard):** After upgrade, all modules compile, the apps still boot, and existing endpoints behave identically. No functional changes are allowed in this plan — only version/compat fixes.
- **Target versions (exact):** Spring Boot `3.4.1` (any later 3.4.x patch is acceptable), Java `21`, MyBatis-Plus `3.5.9` with artifact `mybatis-plus-spring-boot3-starter`, knife4j `4.5.0`.
- **One concern per commit.** Commit after each task. Do not mix the upgrade with AI code (that is Plan 2).
- **No `application.yml` exists in the repo** (config is local/uncommitted). Any yml change in this plan is an instruction for the operator's local file, not a committed file — UNLESS the operator chooses to commit one.
- **Build tool:** Maven. JDK 21 must be installed and selected as `JAVA_HOME` before building.

---

## File Structure (what changes)

- `pom.xml` (root) — Boot parent version, `java.version`, dependency versions, MP artifact in `dependencyManagement`.
- `common/pom.xml` — compiler target 17→21; MP starter artifact `mybatis-plus-boot-starter` → `mybatis-plus-spring-boot3-starter`.
- `model/pom.xml` — compiler target 17→21; same MP artifact switch.
- `web/pom.xml` — compiler target 17→21.
- `web/web-admin/pom.xml` — compiler target 17→21.
- `web/web-app/pom.xml` — compiler target 17→21.
- `common/src/main/java/com/atguigu/lease/common/mybatisplus/MybatisPlusConfiguration.java` — verify/fix (MP 3.5.9 may rename pagination inner-class).
- Source files referencing removed/renamed APIs — fix in Task 3's compile-fix loop.
- (local, uncommitted) `application.yml` for each app — add virtual threads flag.

No new production files. No deletions.

---

## Task 1: Bump root pom — Boot version, Java 21, dependency versions

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: new parent `3.4.1`, `java.version=21`, MP `3.5.9` + artifact `mybatis-plus-spring-boot3-starter`, knife4j `4.5.0`, removal of `spring-amqp.version` override. Child modules inherit these.

- [ ] **Step 1: Change Spring Boot parent version**

In `pom.xml`, replace:
```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.0.5</version>
    </parent>
```
with:
```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
    </parent>
```

- [ ] **Step 2: Set Java 21 + update dependency version properties**

In `pom.xml` `<properties>`, replace:
```xml
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mybatis-plus.version>3.5.3.1</mybatis-plus.version>
        <swagger.version>2.9.2</swagger.version>
        <jwt.version>0.11.2</jwt.version>
        <easycaptcha.version>1.6.2</easycaptcha.version>
        <minio.version>8.2.0</minio.version>
        <knife4j.version>4.1.0</knife4j.version>
        <aliyun.sms.version>2.0.23</aliyun.sms.version>
        <spring-amqp.version>3.1.2</spring-amqp.version>
    </properties>
```
with:
```xml
    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mybatis-plus.version>3.5.9</mybatis-plus.version>
        <swagger.version>2.9.2</swagger.version>
        <jwt.version>0.11.2</jwt.version>
        <easycaptcha.version>1.6.2</easycaptcha.version>
        <minio.version>8.2.0</minio.version>
        <knife4j.version>4.5.0</knife4j.version>
        <aliyun.sms.version>2.0.23</aliyun.sms.version>
        <!-- spring-amqp 版本交给 Spring Boot 3.4 托管,不再手动覆盖 -->
    </properties>
```

- [ ] **Step 3: Switch MyBatis-Plus artifact + version in `dependencyManagement`**

In `pom.xml` `<dependencyManagement>`, replace:
```xml
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-boot-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
```
with:
```xml
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
```

> Why: Spring Boot 3 must use the `mybatis-plus-spring-boot3-starter` artifact; the legacy `mybatis-plus-boot-starter` targets Boot 2.x. Version 3.5.9 is verified compatible with Boot 3.4.

- [ ] **Step 4: Verify root pom is well-formed**

Run: `mvn -q -N help:effective-pom -Doutput=/dev/null`
Expected: BUILD SUCCESS (parses correctly). If it fails on `<java.version>` being unknown — it won't; it's a normal property.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "build: 升级 Spring Boot 3.4.1 + JDK 21 + 依赖版本(mybatis-plus/knife4j)"
```

---

## Task 2: Update child module poms — compiler target 21 + MP artifact switch

**Files:**
- Modify: `common/pom.xml`, `model/pom.xml`, `web/pom.xml`, `web/web-admin/pom.xml`, `web/web-app/pom.xml`

**Interfaces:**
- Consumes: root parent 3.4.1 + `java.version=21` (Task 1).
- Produces: every module compiles under Java 21; `common` and `model` pull the Boot 3 MP starter.

- [ ] **Step 1: Set compiler target 21 in all 5 module poms**

In EACH of `common/pom.xml`, `model/pom.xml`, `web/pom.xml`, `web/web-admin/pom.xml`, `web/web-app/pom.xml`, replace:
```xml
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
```
with:
```xml
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
```

- [ ] **Step 2: Switch MP starter artifact in `common/pom.xml`**

In `common/pom.xml`, replace:
```xml
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
```
with:
```xml
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
```

- [ ] **Step 3: Switch MP starter artifact in `model/pom.xml`**

In `model/pom.xml`, replace:
```xml
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
```
with:
```xml
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
```

- [ ] **Step 4: Commit**

```bash
git add common/pom.xml model/pom.xml web/pom.xml web/web-admin/pom.xml web/web-app/pom.xml
git commit -m "build: 子模块 compiler target 升 21 + 切换 mybatis-plus-spring-boot3-starter"
```

---

## Task 3: Compile all modules + fix breakages (fix-loop)

**Files:**
- Modify: whatever source files fail to compile against Boot 3.4 / MP 3.5.9 / JDK 21 (discovered during this task).

**Interfaces:**
- Produces: `mvn clean compile` BUILD SUCCESS across all 5 modules.

This task is a **diagnose-and-fix loop** — a Boot 3.0→3.4 jump rarely compiles clean on the first try. Iterate until green. Below are the most likely breakages for THIS codebase and the exact fix for each.

- [ ] **Step 1: Ensure JDK 21 is active**

Run: `java -version`
Expected: shows `21` (e.g. `openjdk version "21.0.x"`). If not, set `JAVA_HOME` to a JDK 21 install and re-run. Do not proceed until `java -version` reports 21.

- [ ] **Step 2: Compile the whole reactor**

Run: `mvn clean compile`
Expected: BUILD SUCCESS. If it fails, read the FIRST error only, apply the matching fix from the table below, then re-run `mvn clean compile`. Repeat until BUILD SUCCESS.

- [ ] **Step 3: Fix-loop — common breakages & exact fixes**

| Symptom (first error) | Cause | Fix |
|---|---|---|
| `MybatisPlusInterceptor` / `PaginationInnerInterceptor` import or constructor not found / `DbType` issue | MP 3.5.9 package/class rename | Open `common/src/main/java/com/atguigu/lease/common/mybatisplus/MybatisPlusConfiguration.java`. The interceptor API is unchanged in 3.5.9 (`new MybatisPlusInterceptor()`, `new PaginationInnerInterceptor(DbType.MYSQL)`). If it references an old inner class, rewrite to: `interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));` Keep behavior identical. |
| `spring-doc`/OpenAPI bean conflict or knife4j startup N/A at compile | knife4j 4.5.0 pulls newer springdoc | This is a runtime concern; if a compile error mentions `springdoc.openapi.*`, ensure no hand-written OpenAPI bean overrides version-specific classes. Keep the existing `Knife4jConfiguration` as-is unless it won't compile. |
| `jakarta.servlet.*` not found | (unlikely — already Jakarta on Boot 3.0.5) | No action expected. |
| `class file has wrong version` / unsupported class | leftover `target/` from JDK 17 build | `mvn clean` (already in step 2) — ensure no IDE-managed build cached. |
| RabbitMQ / `spring-amqp` API removed | manual `spring-amqp.version=3.1.2` was removed; Boot 3.4 manages 3.2.x | Usually fine — AMQP API is stable. If `RabbitTemplate`/`@RabbitListener` signatures complain, align usages to the 3.2 API (rare). |
| JDK 21 stricter / removed API (e.g. `SecurityManager`) | none expected in this app | — |

> **Rule:** Every fix in this loop must be a behavioral no-op (API rename, signature alignment) — NOT a functional change. If you are tempted to change behavior, stop and surface it to the operator.

- [ ] **Step 4: Verify compile is green**

Run: `mvn clean compile`
Expected: `BUILD SUCCESS` with all 5 modules listed.

- [ ] **Step 5: Commit any source fixes**

```bash
git add -A
git commit -m "fix: 适配 Spring Boot 3.4 / MyBatis-Plus 3.5.9 / JDK 21 编译"
```
(If Step 3 made no source changes, skip this commit.)

---

## Task 4: Enable virtual threads + verify apps boot (runtime smoke)

**Files:**
- (local, uncommitted) the operator's `application.yml` for `web-admin` and `web-app`. The repo does NOT commit yml — do not create a committed one unless the operator asks.

**Interfaces:**
- Produces: both runnable apps start under Boot 3.4 + JDK 21 with virtual threads enabled.

- [ ] **Step 1: Enable virtual threads in each app's local yml**

In the operator's local `application.yml` (for both `web-admin` and `web-app`), add at the top level:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```
(If a `spring:` block already exists, merge `threads.virtual.enabled: true` under it — do not duplicate the key.)

- [ ] **Step 2: Start one app and confirm it boots**

Precondition: MySQL, Redis, RabbitMQ, MinIO are running and configured in the local yml (same as before upgrade).

Run (from repo root, in one terminal):
```bash
mvn -pl web/web-app -am spring-boot:run
```
Expected: log line `Tomcat started on port ...` / `Started AppWebApplication in X seconds`, no stack traces. Virtual threads active (no extra log required — the flag is enough).

If startup fails, the most likely cause is a yml/bean wiring change in Boot 3.4. Read the first stack trace; typical fixes:
- Deprecated/removed property keys → rename to the Boot 3.4 equivalent.
- Minio/AliyunSms `@ConfigurationProperties` beans still load fine (gated by `@ConditionalOnProperty`).

- [ ] **Step 3: Start the other app and confirm it boots**

Run (separate terminal):
```bash
mvn -pl web/web-admin -am spring-boot:run
```
Expected: `Started AdminWebApplication ...`, no stack traces.

- [ ] **Step 4: No commit (yml is local)**

This task changes only local uncommitted config. Nothing to commit. If the operator decides to commit a template `application.yml`, do that as a separate decision — not required by this plan.

---

## Task 5: Regression gate — build, existing tests, endpoint smoke

**Files:**
- (none — verification only)

**Interfaces:**
- Produces: confidence the upgrade is regression-free. This is the gate that must pass before Plan 2 (AI RAG) starts.

- [ ] **Step 1: Full reactor build + package**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`, all 5 modules build, both app jars produced. This is the **deterministic gate** — it must be green.

- [ ] **Step 2: Run existing unit tests**

Run: `mvn test`
Expected: `BUILD SUCCESS`, `Tests run: N, Failures: 0, Errors: 0`. Note the total `N` for the record. If a pre-existing test now fails, investigate — it must be a real regression to fix, not a test to delete. (The repo has tests under `web/web-app/src/test` and `web/web-admin/src/test`.)

- [ ] **Step 3: Runtime endpoint smoke (if services are up)**

With both apps running (from Task 4), hit representative existing endpoints and confirm they return the same shape as before. Pick one open (no-auth) endpoint and one authed endpoint per app from the existing controllers (do not invent new paths — use real ones already in the codebase):

| App | What to verify | Expected |
|---|---|---|
| web-app | Any open endpoint under `/app/login/**` (e.g. the captcha/SMS or login endpoint that already exists) | 200 `Result` JSON, same `code`/`message` structure as before upgrade |
| web-app | Any authed read endpoint under `/app/**` (e.g. an apartment/room list/detail controller method) with a valid `access-token` | 200, same response payload shape as before |
| web-admin | Any open endpoint under the admin login path | 200 `Result` JSON |
| web-admin | Knife4j doc page `GET /doc.html` | 200 HTML (confirms knife4j 4.5.0 still renders) |

> If a backing service (e.g. RabbitMQ, MinIO) is not running locally, skip only the endpoints that depend on it — but record what was skipped. Step 1 (`mvn clean package`) + Step 2 (`mvn test`) are mandatory and must be green regardless.

- [ ] **Step 4: Record regression result**

Write the result into the task's worktree/commit message or a short note: "Regression: package ✅, tests N ✅, smoke: <list>." No code change here.

- [ ] **Step 5: Final commit (if any verification artifacts) + tag the upgrade**

```bash
git tag jdk21-boot34-upgrade
```
(Only tag if the operator wants a marker. The upgrade is complete once Steps 1–3 are green.)

---

## Verification (end-to-end summary)

The upgrade is **done and verified** when ALL hold:
1. `java -version` → 21.
2. `mvn clean package` → BUILD SUCCESS (all 5 modules).
3. `mvn test` → 0 failures, 0 errors.
4. Both apps boot under Boot 3.4 (Tomcat started, no stack traces).
5. Representative existing endpoints return the same response shape as on Boot 3.0.5.

Once green, the codebase is ready for **Plan 2 — AI RAG feature**, which will be written against this verified upgraded state.

---

## Notes for the next plan (Plan 2 — AI RAG)

After this upgrade is verified, Plan 2 will add (against the real Boot 3.4 codebase + verified Spring AI 1.0 APIs):
- `spring-ai-bom` 1.0.0 + `spring-ai-starter-model-openai` + `spring-ai-starter-vector-store-pgvector` + `spring-ai-tika-document-reader` + `postgresql` driver.
- `common/config/ai/*` (shared AI beans + PG second datasource), `model` `AiKnowledgeDoc` entity.
- `web-app` chat (SSE) + `web-admin` knowledge management + document pipeline.
- All per the spec at `docs/superpowers/specs/2026-06-30-ai-rental-agent-rag-design.md`.
