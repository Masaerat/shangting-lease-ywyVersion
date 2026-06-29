create extension if not exists vector;

create table if not exists ai_rental_knowledge_document (
    id bigserial primary key,
    title varchar(200) not null,
    category varchar(100) not null,
    source varchar(500) not null,
    content text not null,
    embedding vector(1536),
    version varchar(50) not null default 'v1',
    enabled boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_ai_rental_knowledge_enabled
    on ai_rental_knowledge_document(enabled);

create index if not exists idx_ai_rental_knowledge_category
    on ai_rental_knowledge_document(category);

create index if not exists idx_ai_rental_knowledge_embedding_hnsw
    on ai_rental_knowledge_document
    using hnsw (embedding vector_cosine_ops);
