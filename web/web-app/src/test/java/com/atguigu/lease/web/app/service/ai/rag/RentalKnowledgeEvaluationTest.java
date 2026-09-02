package com.atguigu.lease.web.app.service.ai.rag;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.impl.LocalRentalKnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RentalKnowledgeEvaluationTest {

    @Test
    void localFallbackAchievesExpectedCategoryAtRankOneForInterviewDataset() {
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> vectors = mock(ObjectProvider.class);
        when(vectors.getIfAvailable()).thenReturn(null);
        HybridRentalKnowledgeService service = new HybridRentalKnowledgeService(
                vectors, new LocalRentalKnowledgeService(), new RentalQueryRewriter(), new RagProperties());
        List<EvaluationCase> dataset = List.of(
                new EvaluationCase("押金什么时候退？", "DEPOSIT"),
                new EvaluationCase("合同结束后保证金怎么处理？", "DEPOSIT"),
                new EvaluationCase("这个房子支持月付吗？", "PAYMENT"),
                new EvaluationCase("租金能不能按三个月支付？", "PAYMENT"),
                new EvaluationCase("预约看房需要填写什么？", "APPOINTMENT"),
                new EvaluationCase("到访之前要先确认吗？", "APPOINTMENT"),
                new EvaluationCase("入住后水管故障怎么处理？", "REPAIR"),
                new EvaluationCase("普通维修如何报修？", "REPAIR"),
                new EvaluationCase("退租需要走哪些流程？", "CHECKOUT"),
                new EvaluationCase("钥匙交还和费用结算怎么做？", "CHECKOUT"));

        int rankOneHits = 0;
        double reciprocalRankSum = 0;
        for (EvaluationCase item : dataset) {
            KnowledgeSearchResult result = service.search(item.question(), null, 3);
            int rank = rankOf(result.citations(), item.expectedCategory());
            if (rank == 1) rankOneHits++;
            if (rank > 0) reciprocalRankSum += 1.0 / rank;
            assertThat(result.mode()).isEqualTo("LOCAL");
            assertThat(rank)
                    .as("expected category %s for question %s", item.expectedCategory(), item.question())
                    .isEqualTo(1);
            assertThat(result.citations().getFirst().source()).isEqualTo("rag-knowledge.md");
            assertThat(result.citations().getFirst().excerpt()).isNotBlank();
        }

        double hitRateAtOne = rankOneHits / (double) dataset.size();
        double meanReciprocalRank = reciprocalRankSum / dataset.size();
        assertThat(hitRateAtOne).isEqualTo(1.0);
        assertThat(meanReciprocalRank).isEqualTo(1.0);
    }

    private int rankOf(List<KnowledgeCitation> citations, String expectedCategory) {
        for (int index = 0; index < citations.size(); index++) {
            if (expectedCategory.equals(citations.get(index).category())) return index + 1;
        }
        return 0;
    }

    private record EvaluationCase(String question, String expectedCategory) {
    }
}
