# 最终验证记录

更新时间：2026-09-10

本文记录项目已经验证的事实、明确排除的范围和继续上线前需要补做的工作。所有数字都必须连同测试环境一起表述。

## 目录重构回归

本次代码统一迁入 `backend/`，部署资源和脚本收进 `deploy/`，文档扁平化至 `docs/`。当前 Maven Reactor 为 `lease → model → common → web-admin → web-app`，共五个构建项目。前端和重复建表 SQL 已移除，业务 Flyway 迁移保留。

目录重构后已使用 Maven 3.9.9 + JDK 21 容器重新执行完整非集成测试：`web-admin` 4 项、`web-app` 92 项，失败 0、错误 0。五个构建项目全部成功，Compose 配置也已通过静态解析。

## 已验证

- 当前父工程和全部四个子模块编译成功。
- 非集成测试共 96 项通过，失败 0、错误 0：`web-app` 92 项，`web-admin` 4 项。
- 覆盖 Agent 目标与循环、七工具注册、权限、步数/重复调用/超时预算、模型失败降级和脱敏轨迹。
- 覆盖 SSE/JWT 契约、Redis 分层记忆、旧历史兼容、Redis 异常降级、Query Rewrite、Hybrid RAG、RRF 与诊断接口。
- 覆盖预约草稿用户隔离、过期、并发 claim、显式确认、重复确认、MySQL 幂等和 Outbox 创建。
- 覆盖 Outbox 认领代数、Publisher Confirm/Return、失败重试、消息转换、消费重试、唯一通知和幂等已读。
- 30 条版本化 LOCAL RAG 基线中，`HitRate@3`、`MRR`、拒答准确率和同义问题一致性均为 1.0；模式分布为 `LOCAL=24, EMPTY=6`。
- 当前 Docker Compose 配置校验通过，编排只包含 MySQL、Redis、RabbitMQ、PGvector、MinIO、初始化任务和两个后端服务。
- `git diff --check` 通过，仓库配置与文档未包含已知明文模型 Key。

隔离回归命令：

```powershell
docker run --rm `
  -v "${PWD}:/workspace" `
  -w /workspace/backend `
  maven:3.9.9-eclipse-temurin-21 `
  mvn -B -ntp -pl web-app,web-admin -am `
  '-Dtest=*Test,!ScheduledTasksTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

该命令只依赖 Docker，不要求本机安装 JDK 或 Maven。

## 这些数字代表什么

- 96 项是 JUnit/Mockito/MockMvc/H2 范围内的非集成测试，不包含类名以 `IT` 结尾的 Testcontainers 测试。
- H2 MySQL 模式验证 SQL 的主要业务逻辑，但不能完全证明真实 MySQL 的锁、隔离级别和方言行为。
- LOCAL RAG 指标用于保证小规模本地知识库的回归稳定性，不是 PGvector、Embedding 服务或线上用户效果。
- Maven 构建耗时不是接口延迟、吞吐量或模型响应速度，不能写进性能结论。

## 明确未纳入 96 项回归的内容

- `RentalAgentClosedLoopIT`、`AppointmentOutboxPublisherIT`、迁移类 IT 等 Testcontainers 测试。
- 真实大模型与真实 Embedding API 的稳定性、限流、费用和回答质量。
- 大规模 PGvector 知识库的召回率、索引构建时间和查询性能。
- RabbitMQ 节点故障、网络分区、消费者长时间宕机和 DLQ 运维重放演练。
- 多实例会话并发、生产压测、安全渗透、监控告警和灰度发布。

## 上线或答辩前建议补做

1. 在隔离测试环境运行全部 Testcontainers IT，保存 Maven 报告和容器日志。
2. 使用真实模型配置执行 smoke 测试，记录 MODEL/FALLBACK 比例、P95 延迟、错误率和 token 成本。
3. 导入代表真实业务分布的知识文档，扩大评测集并区分向量、词法、混合三组结果。
4. 演练“数据库成功但 Broker 不可用”“消费落库后 ACK 丢失”“消息进入 DLQ”三个故障场景。
5. 检查 RabbitMQ 既有队列参数与 V5 Flyway 迁移，完成备份、迁移和回滚方案。

## 简历与面试口径

可以说“实现了 Transactional Outbox + RabbitMQ 至少一次投递，并通过消费唯一键实现业务效果幂等”，不能说“实现 exactly-once”。

可以说“在 30 条版本化 LOCAL 回归样本上四项指标均为 1.0”，不能把它说成真实 PGvector 或线上模型准确率。

可以说“96 项非集成测试通过，并保留 Testcontainers 闭环用例”，不能说所有真实基础设施集成测试已经包含在这 96 项中。
