# Agent 与 MQ 闭环（2026-09-06）

## 范围与真实能力

Spring AI 仍为 1.0.0，无 MCP、无前端改动。本次增加的是站内通知，不是短信或邮件网关。模型调用只负责查询与准备草稿，业务提交始终需要用户显式确认。

```text
Agent search / RAG / draft → SSE appointment_draft
                                  ↓ 用户显式确认 API
                   MySQL 事务：预约 + token 幂等 + Outbox
                                  ↓ 定时发布、confirm + return 校验
                     RabbitMQ appointment.create.queue
                                  ↓ 消费、MySQL 事务、唯一键
                           user_notification
                                  ↓
              REST 查询 / Agent 查询工具 / 无模型规则查询
```

未授权启动服务，所以真实 RabbitMQ + MySQL + Redis 联调不在本轮执行范围内；不能把编译或内存数据库测试称为真实链路已运行成功。

## Agent 接口

原有 `POST /app/ai/chat`、`POST /app/ai/appointments/draft`、`POST /app/ai/appointments/confirm` 保留。所有接口沿用 `access-token` 身份验证。

新增 SSE payload（外层仍为 `{type,payload}`，SSE event name 仍为 `chat`）：

| type | payload | 含义 |
| --- | --- | --- |
| appointment_draft | confirmationToken、expiresAt、roomId、apartmentId、联系人、预约时间 | 临时草稿，绝不是已提交预约 |
| appointment_status | appointmentId、roomId、appointmentStatus、appointmentTime、deliveryStatus、notificationId；未找到为 null | 用户自己的业务状态与异步投递状态 |
| notifications | 通知数组 | 用户自己的已落库站内通知 |

`done.suggestedAction=CONFIRM_APPOINTMENT` 时客户端从 `appointment_draft.confirmationToken` 调用确认 API。确认返回的 `appointmentId` 才能用于查询预约；不得用 roomId 或 token 代替。

新增 REST：

- `GET /app/ai/appointments/{appointmentId}/status`
- `GET /app/ai/notifications?limit=20`，限制 1–50，按 ID 倒序。
- `POST /app/ai/notifications/{id}/read`，幂等标记已读。

不存在与越权统一返回业务错误码 404（沿用项目 Result 包装，并非承诺 HTTP status 404）。通知内容不包含联系人手机号。Agent 仅注册 `get_appointment_status` / `list_my_notifications` 两个新增只读工具，userId 从可信 ToolContext 取得。

没有模型时可发送“预约123的状态”“我的通知”。缺少预约编号会询问，不从房间编号推断预约。规则模式不是完整多轮自然语言解析器。

## 状态语义

| deliveryStatus | 依据 | 不代表什么 |
| --- | --- | --- |
| PENDING | Outbox 等待发布或发布重试 | 不代表预约没有创建 |
| PUBLISHED | Broker confirm ACK 且无 return | 不代表消费者完成或短信已送达 |
| DELIVERED | 当前用户的 CREATE 站内通知已持久化 | 不代表用户已读、短信或邮件送达 |
| FAILED | Outbox 发布重试达到上限后 DEAD | 不会自动取消预约 |
| UNKNOWN | 旧预约无 Outbox、无通知等 | 不推断成功或失败 |

通知优先于 Outbox 状态：可能消费者已落库、发布器尚未更新 PUBLISHED。消费失败进入 DLQ 时 Outbox 仍可能为 PUBLISHED，因此不能只看 Outbox 判断消费健康；需要同时检查 DLQ。这里没有声称已实现失败状态自动回传。

## 可靠性与边界

- 预约、Outbox、确认 token 幂等沿用同一 MySQL 事务。确认路径不直接写通知。
- 创建消费者直接落站内通知，不再“打印短信 → 未确认地二次转发”。旧通知队列保留给状态变化消息，亦落库。
- CREATE 唯一键是 `appointment:{id}:CREATE`，使有/无 eventId 的旧新创建消息收敛；eventId 同时保存用于关联 Outbox。UPDATE/CANCEL 新消息使用稳定 messageId，老消息退化使用原始创建时间；老消息缺少稳定元数据时去重精度有限。
- 唯一键冲突采用 no-op，不覆盖通知内容、不重置已读时间。失败事务回滚，成功提交之后监听器返回，容器 AUTO ACK。
- 消费有限重试三次；无效消息直接拒绝；最终拒绝进入 DLQ。没有自动消费 DLQ 的监听器，不会打印后把失败消息吃掉。
- Outbox 每次认领一条，30 秒租约，最多等待 10 秒 confirm。完成和失败更新都带 attempts 代数条件，旧发布器不能覆盖新认领状态。网络发送自身仍可能阻塞、进程仍可能暂停，因此仍需数据库幂等；不声称 exactly-once。
- JSON 转换器同时作为 Spring bean 供监听器使用，并用于 RabbitTemplate；业务 JdbcTemplate 显式绑定 MySQL，向量库显式绑定 pgJdbcTemplate。
- 模型失败且已经请求过工具时返回 PARTIAL 轨迹与已有观察，不重跑工具循环。主/备模型共享时间和模型步数预算。取消线程不保证撤销外部操作；草稿最终仍依靠 TTL 与显式确认保护。
- 聊天历史改为 JSON 数组保留多行，兼容旧换行文本，Redis 失败时降级为无历史；未实现跨实例会话并发串行化或长期记忆。

## 部署前必须检查（本轮没有执行）

1. 备份并通过现有 Flyway 流程应用 `V5__user_notification.sql`，不要修改已经执行的 V1–V4。该迁移在 web-app 中；web-admin 与 web-app 共用数据库和消费者，必须先迁移、再同时切换兼容代码，不能让旧消费者继续抢消息。
2. 保持 publisher-confirm-type=correlated、publisher-returns=true、mandatory=true，监听器必须 AUTO 或等价的“提交后 ACK”，不能设 NONE。
3. `appointment.notify.queue` 新增 DLX 参数。已有同名队列不能直接改 immutable arguments，否则会出现 PRECONDITION_FAILED。先停旧生产者/消费者，备份或可靠迁移存量消息，排空后受控重建通知队列再恢复；不要直接删除有消息的队列。也可采用经验证的 Broker policy 迁移方案，但需要同步调整声明，不能盲目混用。
4. `appointment.create.queue` 保留原有两小时 TTL 以兼容现存队列。TTL 是排队过期进入 DLQ，不是“预约两小时后提醒”，也不自动取消预约。
5. 设置 DLQ 数量、Outbox DEAD 数量、最老 PENDING/PUBLISHED 未送达年龄告警。当前仅提供数据基础和日志，不含外部监控平台部署。

## 故障定位与重放

- Broker 不可用：预约仍可事务提交，Outbox 延迟重试；发布错误累积五次后 DEAD。修复 Broker 后，人工检查该事件的预约与通知，再将特定 DEAD 事件恢复 PENDING、设置 next_attempt_at 为当前时间、重置 attempts；必须停止相关发布器并完成旧实例下线再重置代数，避免旧 claim 存活。不要批量无条件重置整个表。
- 消费数据库不可用：消息最终进入 DLQ；恢复数据库与迁移后，保留原消息体、headers、eventId，按原路由重放。看到 publisher confirm 成功且通知存在后再确认处理原死信。混合 DLQ 中 CREATE/旧通知 payload 不同，依 `x-death` 原队列选择 CREATE 或 NOTIFY 路由，不可一律发到创建队列。
- 重复投递：重放同一 CREATE 只保留一条通知；不要以重新调用预约确认/重新创建预约代替消息重放。
- 消费后 ACK 丢失：消息重新投递，唯一键 no-op，已读状态保留。
- DLQ 没记录但状态长期 PUBLISHED：检查消费者存活、队列绑定、JSON 转换器、V5 是否应用、是否还有旧版本消费者；PUBLISHED 不应被界面解释为送达。

## 验证入口

隔离测试（不启动应用/外部服务）：

```powershell
./mvnw.cmd -B -ntp -pl web/web-app,web/web-admin -am '-Dtest=*Test,!ScheduledTasksTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

H2 仅用于离线验证 SQL 幂等/权限/状态逻辑，不能替代 MySQL 方言与锁行为验收。

待用户允许启动测试容器后，单独执行 `RentalAgentClosedLoopIT`：找房与 RAG → 创建草稿 → 确认及重复确认 → 发布前 PENDING/零通知 → RabbitMQ 发布/消费 → REST DELIVERED → Agent 查询 → Broker 重复投递 → 唯一通知与幂等已读。该 IT 会启动随机端口应用和 MySQL/Redis/RabbitMQ 测试容器，**本轮没有运行**。

另有 `AppointmentOutboxPublisherIT` 验证 Broker 暂停/恢复；真实消息失败重放、混合版本升级与消费者宕机测试仍需在测试环境验收。
