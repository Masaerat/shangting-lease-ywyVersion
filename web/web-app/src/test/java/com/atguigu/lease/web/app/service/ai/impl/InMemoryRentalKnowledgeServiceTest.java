package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.web.app.service.ai.model.RentalKnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRentalKnowledgeServiceTest {

    private final InMemoryRentalKnowledgeService service = new InMemoryRentalKnowledgeService();

    @Test
    void searchReturnsDepositKnowledgeForDepositQuestion() {
        List<RentalKnowledgeChunk> chunks = service.search("押金怎么退", 3);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).getTitle().contains("押金"));
    }
}
