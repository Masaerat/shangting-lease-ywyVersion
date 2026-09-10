package com.atguigu.lease.web.app.service.ai.rag;

public record KnowledgeCitation(
        String chunkId,
        Long docId,
        String documentName,
        String category,
        String chapter,
        String section,
        String source,
        Integer version,
        String excerpt,
        double score) {
}
