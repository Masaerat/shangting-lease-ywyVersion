package com.atguigu.lease.web.app.service.ai.impl;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RentalChatServiceImplTest {

    private final RentalChatServiceImpl svc = new RentalChatServiceImpl(null, null, null, null, null);

    @Test
    void buildContext_numbersDocsAndJoinsText() {
        Document d1 = new Document("阳光公寓A-101 月租3500。", Map.of("namespace", "rooms"));
        Document d2 = new Document("文档条款:押一付三。", Map.of("namespace", "doc"));
        String ctx = svc.buildContext(List.of(d1, d2));
        assertTrue(ctx.contains("[1]"), "应带序号 [1]");
        assertTrue(ctx.contains("[2]"), "应带序号 [2]");
        assertTrue(ctx.contains("阳光公寓"));
        assertTrue(ctx.contains("押一付三"));
    }

    @Test
    void toCitations_extractsOnlyRoomsNamespaceAndRoomRef() {
        Document room = new Document("阳光公寓A-101 月租3500。",
                Map.of("namespace", "rooms", "roomRef", 100L, "source", "阳光公寓"));
        Document doc = new Document("文档条款。",
                Map.of("namespace", "doc", "docId", 9L));
        var citations = svc.toCitations(List.of(room, doc));
        assertEquals(1, citations.size(), "只应抽取 namespace=rooms 的文档");
        assertEquals(100L, citations.get(0).getRoomId());
        assertEquals("阳光公寓", citations.get(0).getApartment());
        assertEquals("rooms", citations.get(0).getSource());
    }

    @Test
    void toCitations_emptyOrNullSafe() {
        assertEquals(0, svc.toCitations(null).size());
        assertEquals(0, svc.toCitations(List.of()).size());
    }
}
