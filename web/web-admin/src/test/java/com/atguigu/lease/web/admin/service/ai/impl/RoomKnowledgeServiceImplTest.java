package com.atguigu.lease.web.admin.service.ai.impl;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.model.entity.ApartmentInfo;
import com.atguigu.lease.model.entity.RoomInfo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.junit.jupiter.api.Assertions.*;

class RoomKnowledgeServiceImplTest {

    private final RoomKnowledgeServiceImpl service = new RoomKnowledgeServiceImpl();

    @Test
    void toDocument_containsKeyRoomFactsAndNamespaceRooms() {
        RoomInfo room = new RoomInfo();
        room.setId(100L);
        room.setRoomNumber("A-101");
        room.setRent(new java.math.BigDecimal("3500"));
        ApartmentInfo apt = new ApartmentInfo();
        apt.setId(7L);
        apt.setName("阳光公寓");
        apt.setCityName("北京");
        apt.setDistrictName("朝阳区");

        Document doc = service.toDocument(room, apt);

        String text = doc.getText();
        assertTrue(text.contains("A-101"), "应包含房间号");
        assertTrue(text.contains("3500"), "应包含租金");
        assertTrue(text.contains("阳光公寓"), "应包含公寓名");
        assertEquals("rooms", doc.getMetadata().get("namespace"));
        assertEquals(100L, doc.getMetadata().get("roomRef"));
    }

    @Test
    void toDocument_handlesNullApartment() {
        RoomInfo room = new RoomInfo();
        room.setId(1L);
        room.setRoomNumber("B-202");
        Document doc = service.toDocument(room, null);
        assertEquals("rooms", doc.getMetadata().get("namespace"));
        assertTrue(doc.getText().contains("B-202"));
    }
}
