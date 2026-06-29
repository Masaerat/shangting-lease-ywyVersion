# AI Rental Agent API

## POST `/app/ai/chat`

Ask the AI rental advisor a natural-language room search or rental policy question.

### Authentication

This endpoint is under `/app/**`, so it uses the existing app `access-token` interceptor.

### Request

```json
{
  "sessionId": "optional-session-id",
  "message": "帮我找 2000 左右的房子，并说明押金怎么退",
  "preferences": {
    "provinceId": 1,
    "cityId": 1,
    "districtId": 1,
    "minRent": 1600,
    "maxRent": 2400,
    "paymentTypeId": 1,
    "orderType": "asc"
  }
}
```

### Fields

- `sessionId`: optional conversation id. If omitted, the server returns a generated id.
- `message`: required user message, max 1000 characters.
- `preferences`: optional structured search filters.

### Response

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "optional-session-id",
    "answer": "我按你的描述筛选了可租房源...",
    "recommendedRooms": [
      {
        "roomId": 101,
        "roomNumber": "A101",
        "rent": 2100,
        "apartmentName": "尚庭公寓",
        "address": "浦东新区张江路100号",
        "reason": "尚庭公寓月租金约2100元...",
        "labels": ["近地铁", "采光好"]
      }
    ],
    "citations": [
      {
        "title": "押金与退还",
        "category": "费用规则",
        "source": "docs/ai-rental-agent/rag-knowledge.md",
        "snippet": "押金用于覆盖租期内可能产生的房屋损坏..."
      }
    ],
    "suggestedActions": [
      "查看推荐房源详情",
      "选择意向房源后提交预约看房"
    ]
  }
}
```

### Error Cases

- Empty `message`: returns the existing global error response for `PARAM_ERROR`.
- Overlong `message`: returns the existing global error response for `PARAM_ERROR`.
- Invalid or missing app token: follows existing `/app/**` authentication behavior.

### Compatibility

No existing app endpoint request or response shape is changed.
