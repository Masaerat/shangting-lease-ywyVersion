package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.config.ai.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentKnowledgeServiceImplTest {

    private final RagProperties props = new RagProperties(); // 默认值
    private final DocumentKnowledgeServiceImpl svc =
            new DocumentKnowledgeServiceImpl(null, null, null, null, props);

    @Test
    void buildSplitter_usesRagProperties() {
        TokenTextSplitter s = svc.buildSplitter();
        // 默认 chunkSize=800 token;构造大量**不同**词(重复单字符会被 BPE 合并成极少 token,
        // 无法触发分片),确保 token 数超过 chunkSize,从而 split 出 >1 段。
        String big = IntStream.range(0, 5000)
                .mapToObj(i -> "word" + i)
                .collect(Collectors.joining(" "));
        Document d = new Document(big);
        List<Document> chunks = s.split(List.of(d));
        assertTrue(chunks.size() > 1);
    }
}
