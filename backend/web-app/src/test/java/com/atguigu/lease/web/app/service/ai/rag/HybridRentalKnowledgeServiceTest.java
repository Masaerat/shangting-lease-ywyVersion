package com.atguigu.lease.web.app.service.ai.rag;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.impl.LocalRentalKnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRentalKnowledgeServiceTest {

    @Test
    void mergesVectorAndKeywordResultsAndReranksMatchingCategory() {
        VectorStore vectorStore = mock(VectorStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        Document deposit = Document.builder()
                .id("vec-1")
                .text("完成验房和费用结算后，按合同约定退还押金。")
                .metadata(Map.of(
                        "chunkId", "deposit-1", "docId", 12L, "source", "租房政策.md",
                        "documentName", "租房政策.md", "category", "DEPOSIT",
                        "chapter", "押金", "section", "退还条件", "version", 1))
                .score(0.72)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(deposit));
        LocalRentalKnowledgeService local = mock(LocalRentalKnowledgeService.class);
        when(local.search("押金怎么退"))
                .thenReturn(List.of(new LocalRentalKnowledgeService.KnowledgeSection(
                        "押金与付款", "退租结算后按合同约定退还押金。", "rag-knowledge.md")));
        HybridRentalKnowledgeService service = new HybridRentalKnowledgeService(
                provider, local, new RentalQueryRewriter(), new RagProperties());

        KnowledgeSearchResult result = service.search("押金怎么退", null, 5);

        assertThat(result.mode()).isEqualTo("HYBRID");
        assertThat(result.rewrittenQuery()).contains("退还条件", "费用结算");
        assertThat(result.citations()).hasSize(2);
        assertThat(result.citations().getFirst().chunkId()).isEqualTo("deposit-1");
        assertThat(result.citations().getFirst().score()).isGreaterThan(0.6);
    }

    @Test
    void vectorFailureFallsBackToLocalKnowledge() {
        VectorStore vectorStore = mock(VectorStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("embedding unavailable"));
        LocalRentalKnowledgeService local = mock(LocalRentalKnowledgeService.class);
        when(local.search("如何报修"))
                .thenReturn(List.of(new LocalRentalKnowledgeService.KnowledgeSection(
                        "报修服务", "普通维修会按工单处理。", "rag-knowledge.md")));
        HybridRentalKnowledgeService service = new HybridRentalKnowledgeService(
                provider, local, new RentalQueryRewriter(), new RagProperties());

        KnowledgeSearchResult result = service.search("如何报修", null, 3);

        assertThat(result.mode()).isEqualTo("LOCAL");
        assertThat(result.citations()).singleElement()
                .satisfies(citation -> assertThat(citation.category()).isEqualTo("REPAIR"));
    }

    @Test
    void emptyVectorResultsAreNotReportedAsHybrid() {
        VectorStore vectorStore = mock(VectorStore.class);
        @SuppressWarnings("unchecked") ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        HybridRentalKnowledgeService service = new HybridRentalKnowledgeService(
                provider, new LocalRentalKnowledgeService(), new RentalQueryRewriter(), new RagProperties());

        assertThat(service.search("押金怎么退", null, 3).mode()).isEqualTo("LOCAL");
        assertThat(service.search("健身房几点关门", null, 3).mode()).isEqualTo("EMPTY");
    }

    @Test
    void equalRrfScoresUseStableChunkIdOrder() {
        VectorStore vectorStore = mock(VectorStore.class);
        @SuppressWarnings("unchecked") ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        Document second = Document.builder().id("z-id").text("普通说明")
                .metadata(Map.of("chunkId", "z-id", "category", "GENERAL")).score(0.8).build();
        Document first = Document.builder().id("a-id").text("普通说明")
                .metadata(Map.of("chunkId", "a-id", "category", "GENERAL")).score(0.8).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(second, first));
        LocalRentalKnowledgeService local = mock(LocalRentalKnowledgeService.class);
        when(local.search("普通问题")).thenReturn(List.of());
        RagProperties properties = new RagProperties();
        properties.setVectorWeight(0);
        HybridRentalKnowledgeService service = new HybridRentalKnowledgeService(
                provider, local, new RentalQueryRewriter(), properties);

        assertThat(service.search("普通问题", null, 3).citations())
                .extracting(KnowledgeCitation::chunkId)
                .containsExactly("a-id", "z-id");
    }
}
