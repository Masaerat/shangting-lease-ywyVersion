package com.atguigu.lease.web.app.service.ai.rag;

import com.atguigu.lease.common.utils.JsonUtil;
import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.impl.LocalRentalKnowledgeService;
import com.atguigu.lease.web.app.service.ai.rag.evaluation.RagEvaluationObservation;
import com.atguigu.lease.web.app.service.ai.rag.evaluation.RagEvaluationReport;
import com.atguigu.lease.web.app.service.ai.rag.evaluation.RagEvaluationSample;
import com.atguigu.lease.web.app.service.ai.rag.evaluation.RagRetrievalEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RentalKnowledgeEvaluationTest {

    @Test
    void versionedLocalBaselineCoversParaphrasesAndCorrectlyAbstains() throws IOException {
        @SuppressWarnings("unchecked") ObjectProvider<VectorStore> vectors = mock(ObjectProvider.class);
        when(vectors.getIfAvailable()).thenReturn(null);
        HybridRentalKnowledgeService service = new HybridRentalKnowledgeService(
                vectors, new LocalRentalKnowledgeService(), new RentalQueryRewriter(), new RagProperties());
        List<RagEvaluationSample> dataset = dataset();
        List<RagEvaluationObservation> observations = dataset.stream()
                .map(sample -> new RagEvaluationObservation(sample, service.search(sample.question(), null, 3)))
                .toList();

        RagEvaluationReport report = new RagRetrievalEvaluator().evaluate(observations, 3);

        assertThat(report.datasetVersion()).isEqualTo("v1");
        assertThat(report.samples()).isEqualTo(30);
        assertThat(report.answerableSamples()).isEqualTo(24);
        assertThat(report.unanswerableSamples()).isEqualTo(6);
        assertThat(report.hitRateAtK()).isEqualTo(1.0);
        assertThat(report.meanReciprocalRank()).isEqualTo(1.0);
        assertThat(report.abstentionAccuracy()).isEqualTo(1.0);
        assertThat(report.paraphraseConsistency()).isEqualTo(1.0);
        assertThat(report.modeCounts()).containsEntry("LOCAL", 24L).containsEntry("EMPTY", 6L);
        assertThat(report.modeCounts()).doesNotContainKey("VECTOR").doesNotContainKey("HYBRID");
    }

    private List<RagEvaluationSample> dataset() throws IOException {
        String jsonl = new ClassPathResource("ai/rag-evaluation-dataset.jsonl")
                .getContentAsString(StandardCharsets.UTF_8);
        return jsonl.lines()
                .filter(line -> !line.isBlank())
                .map(line -> JsonUtil.parseObject(line, RagEvaluationSample.class))
                .toList();
    }
}
