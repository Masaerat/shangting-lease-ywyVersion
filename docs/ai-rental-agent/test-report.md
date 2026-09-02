# AI 租房 Agent 后端验收报告

更新时间：2026-09-02

分支：`agentRag`

## 1. 本轮约束

- 只修改后端、测试、脚本和文档，未修改 H5/Admin 前端。
- 未启动 Docker。
- 未新增或重建数据库表；复用本地已有 V1-V4 表结构。
- `.idea/misc.xml` 是用户原有改动，未暂存、未提交。

## 2. 已验证

| 范围 | 结果 |
| --- | --- |
| 改造前业务基线 | 25 tests passed |
| Agent Harness/工具/SSE | 15 tests passed |
| 草稿/确认/幂等/Outbox | 21 tests passed |
| API/JWT/SSE 与预约组合回归 | 22 tests passed |
| RAG 检索回归 | 4 tests passed，其中 10 条离线数据集 HitRate@1=1.00、MRR=1.00 |
| PowerShell smoke 客户端 | `smoke-rental-agent.ps1` 与兼容脚本 AST 语法检查通过 |

以上测试组存在重叠，不能相加后当作“总测试数”。最终全量测试数以 Task 8 的 Maven 输出为准。

## 3. 功能证据

| 能力 | 自动化证据 | 状态 |
| --- | --- | --- |
| 显式模型/工具循环 | `DefaultRentalAgentRuntimeTest` | 通过 |
| 五工具白名单、无确认工具 | `ToolRegistryTest` | 通过 |
| WRITE 拒绝、PREPARE 登录要求 | `PermissionPolicyTest` | 通过 |
| 最大步骤、重复调用、超时 | Agent Runtime/Executor 测试 | 通过 |
| 入住成本 BigDecimal 计算 | `AgentBusinessToolsTest` | 通过 |
| RAG hybrid 合并、rerank、vector fallback | `HybridRentalKnowledgeServiceTest` | 通过 |
| 10 条五分类 LOCAL 评测 | `RentalKnowledgeEvaluationTest` | 通过 |
| 草稿绑定用户、TTL 10 分钟 | Draft/Redis Store 测试 | 通过 |
| 未确认不写预约 | Draft Service 依赖边界与闭环 IT 断言 | 单测通过，在线 IT 待运行 |
| 房源二次校验、排除生效租约 | Confirmation Service + 锁定 SQL | 通过 |
| 预约、幂等、Outbox 同事务调用 | `AppointmentConfirmationServiceTest` | 通过 |
| MQ 失败指数退避 | `AppointmentOutboxPublisherTest` | 通过 |
| JWT + POST SSE 稳定协议 | `AiApiContractTest` | 通过 |
| 登录到“我的预约”完整 HTTP 链路 | `RentalAgentClosedLoopIT`/smoke | 已实现，待依赖可用后运行 |

## 4. 本轮未执行

- `AppointmentConfirmationServiceIT`：需要 MySQL + Redis Testcontainers，验证并发确认只生成一条预约/幂等/Outbox。
- `AppointmentOutboxPublisherIT`：需要容器环境，验证 RabbitMQ 故障恢复。
- `RentalAgentClosedLoopIT`：需要 MySQL + Redis，验证登录、SSE、草稿、确认、重放和预约列表。
- `scripts/smoke-rental-agent.ps1`：本机 8081 未启动且 Redis 6379 未监听，未执行在线 smoke。
- 真实 Luna/OpenAI 兼容模型 tool calling 与回答 Faithfulness：未执行。

未执行项不能在简历或面试中描述为“已通过”。

## 5. 可复现命令

```powershell
# Agent Harness
.\mvnw.cmd -pl web/web-app -am '-Dtest=*Agent*Test,*Tool*Test,*RentalChat*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test

# 预约和 Outbox
.\mvnw.cmd -pl web/web-app -am '-Dtest=*Appointment*Test,*Outbox*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test

# RAG 评测
.\mvnw.cmd -pl web/web-app -am '-Dtest=RentalKnowledgeEvaluationTest,HybridRentalKnowledgeServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test

# 依赖全部启动后
.\scripts\smoke-rental-agent.ps1
```

## 6. 当前结论

Agent + RAG + 预约安全写入的后端代码闭环已经形成，并有非 Docker 自动化证据。完整进程级闭环脚本和容器 IT 已实现但本轮未运行，因此项目可以描述为“完成可运行的后端闭环 Demo 实现”，不能描述为“完成生产部署或完整容器验收”。
