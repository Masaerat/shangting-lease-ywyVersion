# 27公寓项目简历素材

## 推荐项目名称

27公寓智能租房 Agent 与预约闭环系统

## 技术栈

Java 21、Spring Boot、Spring AI、MyBatis-Plus、MySQL、PostgreSQL/PGvector、Redis、RabbitMQ、SSE、Flyway、JUnit 5、Mockito、Testcontainers

## 简历项目描述

面向租房咨询与看房预约场景，在原有公寓业务系统上完成“业务 + Agent + RAG”后端闭环，支持自然语言找房、租赁政策引用、入住成本估算、预约草稿、显式确认与预约结果查询；模型不可用时可降级到本地规则与 Markdown 知识路径。

## 推荐职责写法

- 基于 Spring AI `ToolCallingManager` 设计显式 Agent Harness，完成模型决策、工具调用、Observation 回填和多步推理循环；构建 5 个房源/RAG/费用/草稿领域工具，并加入白名单权限、最大步数、重复调用和分级超时控制。
- 设计 PGvector + 本地 Markdown 混合 RAG：实现标题感知切片、稳定 chunkId、分类/章节/版本/checksum 元数据、Query Rewrite、去重及“向量分 + 分类匹配 + 关键词覆盖”可解释 rerank，统一返回可追溯引用。
- 将预约写入与 Agent 隔离为“Redis 10 分钟草稿 + 用户显式确认”两阶段协议；确认时绑定 JWT 用户、锁定并复核房源，结合 token SHA-256 幂等记录与唯一约束防止越权、重复提交和并发重复预约。
- 使用 Transactional Outbox 将预约、幂等记录和待发布事件置于同一 MySQL 事务，后台通过 RabbitMQ Publisher Confirm、指数退避和失败状态实现可恢复投递；通过 POST SSE 输出推荐、引用、脱敏轨迹和下一步动作。
- 建立 10 条五分类 RAG 离线回归集，LOCAL fallback 达到 `HitRate@1=100%`、`MRR=1.00`；使用 JUnit/Mockito/MockMvc 覆盖工具权限、超时、SSE/JWT 契约、草稿隔离、确认幂等与 Outbox 重试，并保留 Testcontainers 闭环用例。

## 30 秒口述

我在原有公寓租赁系统上做了一个受控租房 Agent。模型通过五个白名单工具查询 MySQL 实时房源和 RAG 政策知识，后端显式管理工具循环、权限、超时和轨迹；模型不可用时会降级到规则引擎。写操作没有交给模型，预约先生成绑定用户的 Redis 草稿，再由确认 API 在一个事务里写预约、幂等记录和 Outbox，从而把 AI 问答接到了可验证的业务闭环。

## 不能写成已完成的内容

- 不写“已生产落地”或“支撑高并发线上流量”。
- 不写真实 Luna/GLM 在线指标，本轮未执行模型 smoke。
- 不写 PGvector 的 MRR=1.00；1.00 是 LOCAL fallback 离线集结果。
- 不写完整 Docker 端到端已通过，本轮按要求未启动 Docker。
