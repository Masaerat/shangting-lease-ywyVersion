package com.atguigu.lease.web.app.service.ai.rag;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.impl.LocalRentalKnowledgeService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridRentalKnowledgeService implements RentalKnowledgeService {

    private static final Logger LOG = LoggerFactory.getLogger(HybridRentalKnowledgeService.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final LocalRentalKnowledgeService localKnowledge;
    private final RentalQueryRewriter queryRewriter;
    private final RagProperties properties;

    public HybridRentalKnowledgeService(ObjectProvider<VectorStore> vectorStoreProvider,
                                        LocalRentalKnowledgeService localKnowledge,
                                        RentalQueryRewriter queryRewriter,
                                        RagProperties properties) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.localKnowledge = localKnowledge;
        this.queryRewriter = queryRewriter;
        this.properties = properties;
    }

    @Override
    public KnowledgeSearchResult search(String question, String category, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        RentalQueryRewriter.RewrittenQuery query = queryRewriter.rewrite(question, category);
        List<KnowledgeCitation> vectorCandidates = List.of();
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null) {
            try {
                List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query.rewritten())
                        .topK(Math.max(safeLimit * 2, properties.getTopK()))
                        .similarityThreshold(properties.getSimilarityThreshold())
                        .filterExpression(new FilterExpressionBuilder().ne("namespace", "rooms").build())
                        .build());
                vectorCandidates = documents == null ? List.of()
                        : documents.stream().map(this::fromVector).toList();
            } catch (RuntimeException error) {
                LOG.warn("Vector knowledge search failed; using lexical fallback: {}",
                        error.getClass().getSimpleName());
            }
        }
        List<KnowledgeCitation> lexicalCandidates = localKnowledge.search(query.original()).stream()
                .map(this::fromLocal)
                .toList();

        List<KnowledgeCitation> result = fuse(vectorCandidates, lexicalCandidates, query).stream()
                .limit(safeLimit)
                .toList();
        return new KnowledgeSearchResult(
                query.original(), query.rewritten(), mode(vectorCandidates, lexicalCandidates), result);
    }

    private KnowledgeCitation fromVector(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new KnowledgeCitation(
                string(metadata.getOrDefault("chunkId", document.getId())),
                longValue(metadata.get("docId")),
                string(metadata.getOrDefault("documentName", metadata.get("source"))),
                string(metadata.getOrDefault("category", "GENERAL")),
                string(metadata.getOrDefault("chapter", "正文")),
                string(metadata.getOrDefault("section", "正文")),
                string(metadata.getOrDefault("source", "vector_store")),
                intValue(metadata.getOrDefault("version", 1)),
                document.getText(),
                document.getScore() == null ? 0.5 : document.getScore());
    }

    private KnowledgeCitation fromLocal(LocalRentalKnowledgeService.KnowledgeSection section) {
        String category = queryRewriter.rewrite(section.title(), null).category();
        return new KnowledgeCitation(
                "local-" + checksum(section.title() + section.excerpt()).substring(0, 16),
                null,
                section.source(),
                category == null ? "GENERAL" : category,
                section.title(),
                section.title(),
                section.source(),
                1,
                section.excerpt(),
                0.45);
    }

    private List<KnowledgeCitation> fuse(List<KnowledgeCitation> vectors,
                                         List<KnowledgeCitation> lexical,
                                         RentalQueryRewriter.RewrittenQuery query) {
        Map<String, FusedCandidate> fused = new LinkedHashMap<>();
        addRanked(fused, vectors, properties.getVectorWeight());
        addRanked(fused, lexical, properties.getLexicalWeight());
        double maximum = (properties.getVectorWeight() + properties.getLexicalWeight())
                / (Math.max(1, properties.getRrfK()) + 1.0);
        return fused.values().stream()
                .map(candidate -> scored(candidate, query, maximum))
                .sorted(Comparator.comparingDouble(KnowledgeCitation::score).reversed()
                        .thenComparing(this::dedupKey))
                .toList();
    }

    private void addRanked(Map<String, FusedCandidate> fused, List<KnowledgeCitation> citations, double weight) {
        int rank = 1;
        for (KnowledgeCitation citation : citations) {
            String key = dedupKey(citation);
            double contribution = weight / (Math.max(1, properties.getRrfK()) + rank);
            fused.compute(key, (ignored, existing) -> existing == null
                    ? new FusedCandidate(citation, contribution)
                    : new FusedCandidate(prefer(existing.citation(), citation), existing.rrfScore() + contribution));
            rank++;
        }
    }

    private KnowledgeCitation prefer(KnowledgeCitation first, KnowledgeCitation second) {
        if (first.docId() == null && second.docId() != null) return second;
        return first;
    }

    private KnowledgeCitation scored(FusedCandidate candidate,
                                      RentalQueryRewriter.RewrittenQuery query,
                                      double maximum) {
        KnowledgeCitation citation = candidate.citation();
        double normalized = maximum <= 0 ? 0 : candidate.rrfScore() / maximum;
        if (query.category() != null && query.category().equalsIgnoreCase(citation.category())) {
            normalized += properties.getCategoryBoost();
        }
        return new KnowledgeCitation(
                citation.chunkId(), citation.docId(), citation.documentName(), citation.category(),
                citation.chapter(), citation.section(), citation.source(), citation.version(),
                citation.excerpt(), Math.min(1.0, normalized));
    }

    private String mode(List<KnowledgeCitation> vectors, List<KnowledgeCitation> lexical) {
        if (!vectors.isEmpty() && !lexical.isEmpty()) return "HYBRID";
        if (!vectors.isEmpty()) return "VECTOR";
        if (!lexical.isEmpty()) return "LOCAL";
        return "EMPTY";
    }

    private String dedupKey(KnowledgeCitation citation) {
        if (citation.chunkId() != null && !citation.chunkId().isBlank()) {
            return citation.chunkId();
        }
        return citation.source() + "|" + citation.excerpt();
    }

    private String checksum(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer intValue(Object value) {
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private record FusedCandidate(KnowledgeCitation citation, double rrfScore) {
    }
}
