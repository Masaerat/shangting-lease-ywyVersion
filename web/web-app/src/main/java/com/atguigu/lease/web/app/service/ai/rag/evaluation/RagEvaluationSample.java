package com.atguigu.lease.web.app.service.ai.rag.evaluation;

/** One versioned golden-dataset row used by the offline retrieval benchmark. */
public record RagEvaluationSample(
        String id,
        String datasetVersion,
        String paraphraseGroup,
        String question,
        String expectedCategory,
        boolean answerable) {
}
