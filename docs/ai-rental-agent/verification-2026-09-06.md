# Agent / MQ 升级验证记录

日期：2026-09-06；分支：agentRag。实现随后已提交并推送为 `febf895`；本文件记录的是提交前执行的验证事实。

## 已执行

- 后端 reactor 编译成功，包含 model、common、web-admin、web-app。
- 最终隔离回归：31 个测试类、87 项测试，失败 0、错误 0、跳过 0；其中 web-app 83 项、web-admin 4 项。最终 Maven 耗时 59.257 秒（依赖已缓存，不是接口性能数据）。
- `git diff --check` 通过；未修改前端；Spring AI BOM 保持 1.0.0。

命令：

```powershell
./mvnw.cmd -B -ntp -pl web/web-app,web/web-admin -am '-Dtest=*Test,!ScheduledTasksTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

本机原来没有可用 JDK，使用临时目录中的便携 Microsoft JDK 21；Maven 3.9.9 来自项目 wrapper，没有修改系统全局 JAVA_HOME。

## 本轮新增/更新的重点覆盖

- CREATE 重复消息与旧格式消息收敛到一条通知，重复投递不重置已读状态。
- 通知事务写入、所有权检查、列表限制、幂等已读、匿名与越权查询拒绝。
- PENDING / PUBLISHED / FAILED / DELIVERED / UNKNOWN 区分，以及消费先于发布状态更新的竞态。
- 消费数据库异常三次后拒绝，不吞异常；瞬时错误可恢复；非法事件不重试。
- Outbox claim 过期再认领、仅认领一条、过期代数不能覆盖新代数、失败错误长度限制。
- Broker ACK、NACK、ACK 但 return，以及生产/消费共享 JSON 转换器。Broker 行为使用 mock，不是实际 Broker 验收。
- 真实 Spring AI ToolCallingManager + 真实工具注册/包装器调用预约查询工具；ChatModel 和数据存储 mock，无外部模型请求。
- 草稿 SSE 含 confirmationToken；模型后续失败保留已有草稿观察；不重跑工具；共用预算状态检查。
- 无模型查询预约/通知、编号缺失询问、不把 roomId 当作预约编号。
- JSON 历史保留多行、旧历史兼容、Redis 异常降级；无关政策检索不输出无关证据。

SQL 离线测试使用 H2 MySQL 模式。它验证本轮 SQL 的业务逻辑，不足以证明 MySQL 锁、真实网络、Broker ACK 时序的全部性质。

## 明确未执行

- 未启动业务项目、Docker、MySQL、Redis、RabbitMQ、PostgreSQL、MinIO。
- 未调用真实大模型或 embedding 服务，没有消耗模型 API 额度。
- 未运行 `*IT`，也未运行原来会启动应用并连接数据库的 `ScheduledTasksTest`。这些是明确排除，不计入“跳过 0”的分母。
- V5 迁移没有应用到任何真实数据库；未修改现有 RabbitMQ 队列。

`RentalAgentClosedLoopIT` 已扩展为包含真实 RabbitMQ 容器的测试并通过 testCompile：确认 → Outbox → Broker → 持久化 → REST/Agent 查询 → 重复消息/已读。须得到允许启动测试环境后运行；本轮不能声称真实基础设施闭环已经跑通。

## 上线前风险

先读 `mq-closed-loop.md` 的迁移和重放说明。特别注意：通知队列新 DLX 参数与既有队列不兼容时需要受控迁移；旧版本消费者必须退出，否则仍可能抢消息并只打印日志。消费者失败进 DLQ 后 Outbox 可仍显示 PUBLISHED，必须联合监控 DLQ，不能把它当送达。

剩余工程方向包括跨实例会话并发、结构化偏好记忆、完整文档索引生命周期、检索评测扩容、消费失败状态自动回传与运维重放 API；这些没有在本次核心闭环交付中被宣称实现。MCP、Spring AI 升级按用户要求不做。

## 后续：分层记忆与 RAG 评测增量验证

同日继续在 `agentRag` 分支完成了分层上下文记忆与 RAG 可重复评测。本节对应 `febf895` 之后的增量代码，不改变上文提交前验证的历史事实。

- Docker Maven 定向测试：22 项，失败 0、错误 0、跳过 0，覆盖 RAG 诊断接口、聊天接入、分层记忆、RRF 和 V1 评测集。
- `web-app` 全量非集成测试：92 项，失败 0、错误 0、跳过 0。
- `web-admin` 知识切块、文档入库和房源入库测试：4 项，失败 0、错误 0、跳过 0。
- 根项目六模块 `mvn -B -DskipTests compile` 成功。
- V1 本地知识基线：30 条样本，HitRate@3、MRR、拒答准确率、同义问题一致性均为 1.0；模式分布为 `LOCAL=24, EMPTY=6`。
- 当前 PGVector 无文档，因此没有把上述本地指标表述为 Embedding 或真实向量效果。
- `git diff --check` 无空白错误；只有 Windows 工作区的 LF/CRLF 转换提示。

完整设计、测试方法和面试表达见 `context-memory-and-rag-evaluation.md`。
