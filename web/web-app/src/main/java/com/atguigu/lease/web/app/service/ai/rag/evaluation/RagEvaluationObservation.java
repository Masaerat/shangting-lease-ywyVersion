package com.atguigu.lease.web.app.service.ai.rag.evaluation;

import com.atguigu.lease.web.app.service.ai.rag.KnowledgeSearchResult;

public record RagEvaluationObservation(
        RagEvaluationSample sample,
        KnowledgeSearchResult result) {
}
