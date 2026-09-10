package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.model.entity.AiKnowledgeDoc;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredKnowledgeChunkerTest {

    @Test
    void createsStableTraceableChunksFromMarkdownHeadings() {
        AiKnowledgeDoc source = new AiKnowledgeDoc()
                .setDocName("租房政策.md")
                .setNamespace("rental-policy");
        source.setId(12L);
        source.setCreateTime(new Date(0));
        StructuredKnowledgeChunker chunker = new StructuredKnowledgeChunker(new RagProperties());
        Document raw = new Document("""
                # 押金
                ## 退还条件
                完成验房和费用结算后，按合同约定退还押金。
                # 报修服务
                紧急安全问题应立即联系公寓前台。
                """);

        List<Document> first = chunker.split(source, List.of(raw));
        List<Document> second = chunker.split(source, List.of(raw));

        assertThat(first).hasSize(2);
        assertThat(first).extracting(Document::getId)
                .containsExactlyElementsOf(second.stream().map(Document::getId).toList());
        assertThat(first.getFirst().getMetadata())
                .containsEntry("docId", 12L)
                .containsEntry("documentName", "租房政策.md")
                .containsEntry("chapter", "押金")
                .containsEntry("section", "退还条件")
                .containsEntry("category", "DEPOSIT")
                .containsEntry("namespace", "rental-policy")
                .containsKeys("chunkId", "checksum", "version", "effectiveDate");
    }
}
