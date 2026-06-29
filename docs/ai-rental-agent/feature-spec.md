# AI Rental Agent Feature Spec

## Goal

Add an AI rental advisor for the app side. The advisor helps tenants search rooms with natural language and answers stable rental questions such as deposit, appointment, rent payment, move-in, repair, renewal, and checkout rules.

## Scope

- Add a new app endpoint: `POST /app/ai/chat`.
- Reuse existing room search service for live room data.
- Keep rental policy knowledge separate from room inventory.
- Return recommended rooms, knowledge citations, and suggested next actions.
- Do not change existing room, apartment, appointment, login, or lease endpoints.

## Behavior

- Room search questions call the room-search tool backed by MySQL.
- Rental policy questions call the knowledge retrieval service.
- Mixed questions can use both tools in one response.
- Sensitive actions such as appointment submission, contract signing, and payment are not executed by AI. The response only suggests next steps.

## Architecture

- `AiChatController` owns the public app API.
- `AiRentalAgentService` coordinates request validation, intent parsing, room search, and knowledge retrieval.
- `RentalRoomToolService` wraps existing room query behavior as a read-only agent tool.
- `RentalKnowledgeService` is the RAG boundary. The first implementation uses local curated knowledge so the app can run without external AI services.
- `db/ai-rental-agent/pgvector-schema.sql` defines the target pgvector schema for the production RAG store.

## Future Spring AI Integration

The current project uses Spring Boot 3.0.5. To avoid breaking existing features, this first version keeps the AI provider behind service interfaces and does not force Spring AI dependencies into the runtime path.

When upgrading the platform, use Spring Boot 3.4.x with Spring AI 1.0.x, then replace or extend `RentalKnowledgeService` with PGvector VectorStore retrieval and add a cloud model provider for final answer generation.
