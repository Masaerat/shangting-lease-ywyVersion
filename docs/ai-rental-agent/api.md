# AI 租房助手 API

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
| `meta` | `{ mode: "MODEL" | "FALLBACK", conversationId }` |
| `message` | 增量回答文本 |
| `recommendations` | `{ roomId, apartmentId, apartment, roomNumber, rent }[]` |
| `citations` | `{ roomId, apartment, roomNumber, rent, source }[]` |
| `done` | `null` |
| `error` | 可展示的错误信息 |

聊天接口是只读边界，不会创建预约。H5 使用 `fetch` 发送带认证头的 POST，并解析响应流。

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

返回当前登录用户的看房预约列表。H5 确认成功后跳转到 `/myAppointment?appointmentId=<id>`。
