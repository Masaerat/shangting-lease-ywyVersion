package com.atguigu.lease.web.app.service.ai.rag.evaluation;

import java.util.Map;

public record RagEvaluationReport(
        String datasetVersion,
        int samples,
        int answerableSamples,
        int unanswerableSamples,
        int k,
        double hitRateAtK,
        double meanReciprocalRank,
        double abstentionAccuracy,
        double paraphraseConsistency,
        Map<String, Long> modeCounts) {
}
