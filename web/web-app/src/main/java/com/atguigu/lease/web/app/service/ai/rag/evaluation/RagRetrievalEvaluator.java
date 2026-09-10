package com.atguigu.lease.web.app.service.ai.rag.evaluation;

import com.atguigu.lease.web.app.service.ai.rag.KnowledgeCitation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic retrieval metrics. Answer faithfulness is evaluated separately. */
@Component
public class RagRetrievalEvaluator {

    public RagEvaluationReport evaluate(List<RagEvaluationObservation> observations, int requestedK) {
        if (observations == null || observations.isEmpty()) {
            throw new IllegalArgumentException("RAG evaluation observations are required");
        }
        int k = Math.max(1, requestedK);
        int answerable = 0;
        int hits = 0;
        double reciprocalRank = 0;
        int unanswerable = 0;
        int correctAbstentions = 0;
        Map<String, Long> modes = new LinkedHashMap<>();

        for (RagEvaluationObservation observation : observations) {
            RagEvaluationSample sample = observation.sample();
            modes.merge(observation.result().mode(), 1L, Long::sum);
            if (sample.answerable()) {
                answerable++;
                int rank = rankOf(observation, k);
                if (rank > 0) {
                    hits++;
                    reciprocalRank += 1.0 / rank;
                }
            } else {
                unanswerable++;
                if (observation.result().citations().isEmpty()) correctAbstentions++;
            }
        }

        return new RagEvaluationReport(
                datasetVersion(observations),
                observations.size(),
                answerable,
                unanswerable,
                k,
                ratio(hits, answerable),
                answerable == 0 ? 0 : reciprocalRank / answerable,
                ratio(correctAbstentions, unanswerable),
                paraphraseConsistency(observations, k),
                Map.copyOf(modes));
    }

    private int rankOf(RagEvaluationObservation observation, int k) {
        List<KnowledgeCitation> citations = observation.result().citations();
        int bound = Math.min(k, citations.size());
        for (int index = 0; index < bound; index++) {
            if (observation.sample().expectedCategory().equalsIgnoreCase(citations.get(index).category())) {
                return index + 1;
            }
        }
        return 0;
    }

    private double paraphraseConsistency(List<RagEvaluationObservation> observations, int k) {
        Map<String, List<RagEvaluationObservation>> groups = observations.stream()
                .filter(item -> item.sample().answerable())
                .filter(item -> item.sample().paraphraseGroup() != null && !item.sample().paraphraseGroup().isBlank())
                .collect(Collectors.groupingBy(item -> item.sample().paraphraseGroup(), LinkedHashMap::new, Collectors.toList()));
        List<Double> similarities = new ArrayList<>();
        for (List<RagEvaluationObservation> group : groups.values()) {
            for (int left = 0; left < group.size(); left++) {
                for (int right = left + 1; right < group.size(); right++) {
                    similarities.add(jaccard(ids(group.get(left), k), ids(group.get(right), k)));
                }
            }
        }
        return similarities.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
    }

    private Set<String> ids(RagEvaluationObservation observation, int k) {
        return observation.result().citations().stream()
                .limit(k)
                .map(this::identity)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String identity(KnowledgeCitation citation) {
        if (citation.chunkId() != null && !citation.chunkId().isBlank()) return citation.chunkId();
        return citation.source() + "|" + citation.chapter() + "|" + citation.section();
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 1.0 : intersection.size() / (double) union.size();
    }

    private String datasetVersion(List<RagEvaluationObservation> observations) {
        Set<String> versions = observations.stream()
                .map(item -> item.sample().datasetVersion())
                .collect(Collectors.toSet());
        if (versions.size() != 1) throw new IllegalArgumentException("Dataset versions must not be mixed");
        return versions.iterator().next();
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : numerator / (double) denominator;
    }
}
