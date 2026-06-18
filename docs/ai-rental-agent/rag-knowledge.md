# RAG Knowledge Guide

## Knowledge Boundary

The RAG store is for stable rental knowledge only:

- Deposit and refund rules.
- Rent and payment method explanation.
- Appointment process.
- Move-in checklist.
- Repair and maintenance process.
- Renewal, checkout, and breach reminders.

Room inventory, rent amount, release status, and lease occupancy must stay in MySQL and be queried live through agent tools.

## Initial Knowledge

### 押金与退还

押金用于覆盖租期内可能产生的房屋损坏、欠费或违约费用。退租时应先完成房屋验收、水电物业结清，再按合同约定退还押金。

### 预约看房流程

租客可以先选择意向房源，再提交预约看房时间。AI 不直接替用户提交预约，只生成建议和草稿，最终预约动作必须由用户确认。

### 租金与付款方式

房源页面展示月租金，具体付款方式以房源支持的付款类型和合同约定为准，常见方式包括月付、季付和押一付三。

### 维修与入住

入住前建议核对门锁、家电、水电表、家具和网络状态。租期内设施故障应及时联系公寓管理员或客服登记维修。

## Chunking Rules

- Each chunk should focus on one topic.
- Recommended chunk length: 200 to 500 Chinese characters.
- Keep source title and category for citation display.
- Do not place secrets, phone verification codes, private user data, or unpublished room data in knowledge chunks.

## Update Flow

1. Edit this source document or approved Markdown files.
2. Run the ingestion job after PGvector integration is enabled.
3. Verify retrieval with representative tenant questions.
4. Record test results in `docs/ai-rental-agent/test-report.md`.
