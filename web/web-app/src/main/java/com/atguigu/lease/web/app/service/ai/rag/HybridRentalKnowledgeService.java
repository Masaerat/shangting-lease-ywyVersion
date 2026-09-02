package com.atguigu.lease.web.app.service.ai.rag;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.impl.LocalRentalKnowledgeService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HybridRentalKnowledgeService implements RentalKnowledgeService {

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
        List<KnowledgeCitation> candidates = new ArrayList<>();
        boolean vectorSucceeded = false;
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null) {
            try {
                List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query.rewritten())
                        .topK(Math.max(safeLimit * 2, properties.getTopK()))
                        .similarityThreshold(properties.getSimilarityThreshold())
                        .filterExpression(new FilterExpressionBuilder().ne("namespace", "rooms").build())
                        .build());
                candidates.addAll(documents.stream().map(this::fromVector).toList());
                vectorSucceeded = true;
            } catch (RuntimeException ignored) {
                vectorSucceeded = false;
            }
        }
        candidates.addAll(localKnowledge.search(query.original()).stream()
                .map(this::fromLocal)
                .toList());

        Map<String, KnowledgeCitation> unique = new LinkedHashMap<>();
        candidates.stream()
                .map(citation -> rerank(citation, query))
                .sorted(Comparator.comparingDouble(KnowledgeCitation::score).reversed())
                .forEach(citation -> unique.putIfAbsent(dedupKey(citation), citation));
        List<KnowledgeCitation> result = unique.values().stream().limit(safeLimit).toList();
        return new KnowledgeSearchResult(
                query.original(), query.rewritten(), vectorSucceeded ? "HYBRID" : "LOCAL", result);
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

    private KnowledgeCitation rerank(KnowledgeCitation citation, RentalQueryRewriter.RewrittenQuery query) {
        double score = citation.score();
        if (query.category() != null && query.category().equalsIgnoreCase(citation.category())) {
            score += 0.25;
        }
        String searchable = (citation.chapter() + " " + citation.section() + " " + citation.excerpt())
                .toLowerCase(Locale.ROOT);
        long hits = query.terms().stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .filter(searchable::contains)
                .count();
        score += Math.min(0.2, hits * 0.04);
        return new KnowledgeCitation(
                citation.chunkId(), citation.docId(), citation.documentName(), citation.category(),
                citation.chapter(), citation.section(), citation.source(), citation.version(),
                citation.excerpt(), Math.min(score, 1.0));
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
}
