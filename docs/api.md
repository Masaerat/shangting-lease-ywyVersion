# AI 租房助手 API

本文列出智能租房后端的主要调试接口。MQ 状态语义、失败处理与重放方法见 [Agent 与 MQ 闭环](mq-closed-loop.md)。

所有业务接口使用 `/app` 前缀。除登录外，请求头必须携带：

```text
access-token: <登录返回的 JWT>
```

统一 JSON 响应为 `{ "code": 200, "message": "成功", "data": ... }`。

## 演示登录

`POST /app/login`

```json
{
  "phone": "13800000000",
  "code": "888888"
}
```

固定验证码只在 `app.demo-login.enabled=true` 时对演示手机号生效。

## SSE 对话

`POST /app/ai/chat`，响应类型为 `text/event-stream`。

```json
{
  "conversationId": "demo-conversation",
  "message": "预算2500元，并说明押金怎么退"
}
```

服务端发送名为 `chat` 的 SSE 事件，每个 `data` 是一个 `ChatSseEvent`：

| `type` | `payload` |
| --- | --- |
| `meta` | `{ mode: "MODEL" | "FALLBACK", conversationId, provider, traceId }` |
| `message` | 增量回答文本 |
| `recommendations` | `{ roomId, apartmentId, apartment, roomNumber, rent }[]` |
| `citations` | `{ chunkId, documentName, category, chapter, section, source, version, excerpt, score }[]` |
| `trajectory` | MODEL 模式可选的脱敏执行轨迹 `{ step, model, tool, status, elapsedMs, resultCount, errorType }[]` |
| `appointment_draft` | 待确认预约的 `confirmationToken`、过期时间和规范化草稿；不代表已经创建预约 |
| `appointment_status` | 当前用户预约的业务状态、投递状态和通知 ID |
| `notifications` | 当前用户的站内通知数组 |
| `done` | `{ traceId, suggestedAction: "SELECT_ROOM" | "CONFIRM_APPOINTMENT" | "NONE" }` |
| `error` | 可展示的错误信息 |

MODEL 与 FALLBACK 使用同一套事件外壳，客户端不解析模型原始 tool call。Agent 最多调用草稿工具，正式预约只能由下方确认接口写入；普通找房聊天不会创建预约。

Knife4j 可以展示接口定义，但不适合观察持续到达的 SSE 分片。调试本接口建议使用 Postman、Apifox 或 `curl.exe -N`。

## RAG 检索诊断

`GET /app/ai/rag/search`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `question` | 是 | 原始问题，不能为空且不超过 1000 个字符 |
| `category` | 否 | 期望知识分类，用于过滤或加权 |
| `limit` | 否 | 返回数量，默认 5 |

示例：

```text
GET /app/ai/rag/search?question=押金什么时候退&category=deposit&limit=5
```

响应会显示原始查询、改写查询、实际检索模式和排序后的引用。`LOCAL` 表示本地词法知识命中，`VECTOR`/混合模式才说明向量检索实际参与；不能只根据最终有答案就断言 PGvector 生效。

## 预约草稿

`POST /app/ai/appointments/draft`

```json
{
  "roomId": 930001,
  "name": "演示用户",
  "phone": "13800000000",
  "appointmentTime": "2026-08-06T14:00:00",
  "additionalInfo": "希望提前电话联系"
}
```

响应包含 `confirmationToken`、`expiresAt` 和规范化后的草稿。草稿保存在 Redis，10 分钟失效；此步骤不会写入预约表。

## 明确确认

`POST /app/ai/appointments/confirm`

```json
{
  "confirmationToken": "<draft token>"
}
```

响应包含 `appointmentId` 和 `idempotentReplay`。首次确认在同一 MySQL 事务中写入预约、幂等记录和 Outbox；相同用户重放同一 token 返回原预约 ID。

## 查询结果

`GET /app/appointment/listItem`

返回当前登录用户的看房预约列表。确认接口返回的 `appointmentId` 可继续用于查询下方的预约投递状态。

## 预约投递状态

`GET /app/ai/appointments/{appointmentId}/status`

只允许查询当前登录用户自己的预约。核心字段包括 `appointmentStatus`、`deliveryStatus` 和 `notificationId`。

| `deliveryStatus` | 含义 |
| --- | --- |
| `PENDING` | Outbox 等待发布或重试 |
| `PUBLISHED` | RabbitMQ Broker 已 ACK，尚不能证明消费完成 |
| `DELIVERED` | 当前实现的站内通知已经持久化 |
| `FAILED` | Outbox 发布达到重试上限 |
| `UNKNOWN` | 旧数据等场景没有足够信息判断 |

## 站内通知

查询当前用户通知：

```text
GET /app/ai/notifications?limit=20
```

`limit` 允许 1–50，结果按 ID 倒序。通知内容不包含联系人手机号。

幂等标记已读：

```text
POST /app/ai/notifications/{id}/read
```

不存在和越权访问都返回项目统一业务错误码 404，避免泄露其他用户数据是否存在。
