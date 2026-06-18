# AI Rental Agent Test Report

## Automated Tests

Executed on feature branch `feature/ai-rental-agent-rag`:

- `AiChatRequestValidatorTest`: passed.
- `RentalIntentParserTest`: passed.
- `InMemoryRentalKnowledgeServiceTest`: passed.
- `AiRentalAgentServiceImplTest`: passed.

Command:

```bash
C:\Users\admin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd -pl web/web-app -Dtest=AiChatRequestValidatorTest,RentalIntentParserTest,InMemoryRentalKnowledgeServiceTest,AiRentalAgentServiceImplTest test
```

Compile verification:

```bash
C:\Users\admin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd -pl web/web-app -DskipTests compile
```

Result: passed.

## Manual Regression Checklist

- Existing room list endpoint still returns the original response shape.
- Existing room detail endpoint still returns the original response shape.
- Existing apartment detail endpoint still returns the original response shape.
- Existing login and `/app/**` authentication behavior remains unchanged.

## Scenario Checklist

- User asks: `预算 2000，近地铁，一室一厅`.
  - Expected: room search is requested and recommended rooms are returned if matching data exists.
- User asks: `押金怎么退`.
  - Expected: RAG citation includes deposit refund knowledge.
- User asks: `推荐房源并说明押金规则`.
  - Expected: response can include both recommended rooms and deposit citation.
- User sends an empty message.
  - Expected: parameter validation fails.
