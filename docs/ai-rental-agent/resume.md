# 27 公寓智能租房 Agent：简历素材

> 下面内容按“Java 后端、Agent、RAG、上下文、MQ、质量保障”拆分，避免把所有技术堆在同一个句子里。请结合自己的实际理解改写，不要整段照搬。

## 推荐项目名称

27 公寓智能租房 Agent 与可靠预约闭环系统

## 技术栈

Java 21、Spring Boot 3.4.1、Spring AI 1.0.0、MyBatis-Plus、MySQL、Redis、RabbitMQ、PostgreSQL/PGvector、Flyway、SSE、JUnit 5、Mockito、Testcontainers、Docker Compose

## 项目描述

在公寓、房源、用户、预约和租约等真实租赁业务基础上，设计智能租房 Agent，将自然语言找房、租赁知识问答、入住成本计算、预约草稿、显式确认、可靠事件投递和站内通知串成后端闭环；模型或向量服务不可用时可降级到本地规则与知识检索。

## 推荐职责写法

- **Java 业务**：基于 Java 21、Spring Boot 3.4.1 与 MyBatis-Plus 维护公寓、房间、用户、预约和租约模块，使用 JWT 拦截器传递可信用户身份，并通过 Flyway 管理预约幂等、Outbox 与通知表结构。
- **Agent**：基于 Spring AI `ToolCallingManager` 实现显式 Agent Runtime，统一管理模型决策、Observation 回填和多步循环；注册 7 个租房领域工具，并通过权限白名单、最大步数、重复调用限制和分级超时约束执行边界。
- **RAG**：构建 PGvector 语义检索与本地词法检索的混合链路，实现标题感知切片、稳定 `chunkId`、Query Rewrite、RRF 融合和分类加权，输出文档、章节、版本、摘要和分数等可追溯引用。
- **上下文记忆**：使用 Redis 保存结构化租房偏好、历史摘要和最近消息，在字符预算内按完整轮次压缩旧对话，兼容旧格式历史并在 Redis 异常时降级，减少滑动窗口直接丢失重要约束的问题。
- **预约与 MQ**：将高风险写操作拆成“Redis 预约草稿 + 用户显式确认”，确认时在同一 MySQL 事务写入预约、token 幂等记录和 Transactional Outbox；结合 RabbitMQ Confirm/Return、重试认领和消费唯一键实现至少一次投递下的业务幂等。
- **质量保障**：设计 30 条版本化 RAG 回归集并实现 HitRate@K、MRR、拒答准确率和同义问题一致性指标；通过 96 项非集成测试覆盖 Agent、权限、SSE、记忆、预约和 Outbox，同时保留 Testcontainers 真实基础设施闭环用例。

## 30 秒口述

我在原有公寓租赁系统上做了一个受控的智能租房 Agent。它不是简单调一次大模型，而是由后端显式管理七个领域工具，查询 MySQL 的真实房源和带引用的 RAG 知识。上下文采用状态、摘要和最近消息三层记忆。涉及预约时，模型只能生成 Redis 草稿，用户显式确认后才在一个事务里写预约、幂等记录和 Outbox，再通过 RabbitMQ 异步生成站内通知，因此形成了从问答到真实业务结果的完整闭环。

## 面试时必须主动说明的边界

- 30 条样本的 1.0 指标属于 LOCAL 小规模回归集，不是 PGvector 或线上模型准确率。
- 96 项是非集成测试；Testcontainers IT、真实模型稳定性和生产压测需要单独表述。
- Outbox + 幂等消费不是 exactly-once，而是至少一次投递下的业务效果幂等。
- `DELIVERED` 表示站内通知落库，不表示短信、邮件送达或用户已读。
- 单 JVM 条带锁没有解决多实例会话并发，生产化需要 Redis Lua/CAS 或分布式锁。

完整原理、源码落点和追问题库见[升级改造全解与面试手册](agent-rag-mq-interview-handbook.md)。
