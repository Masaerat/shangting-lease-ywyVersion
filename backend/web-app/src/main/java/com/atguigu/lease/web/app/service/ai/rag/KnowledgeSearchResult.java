package com.atguigu.lease.web.app.service.ai.rag;

import java.util.List;

public record KnowledgeSearchResult(
        String originalQuery,
        String rewrittenQuery,
        String mode,
        List<KnowledgeCitation> citations) {

    public KnowledgeSearchResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
