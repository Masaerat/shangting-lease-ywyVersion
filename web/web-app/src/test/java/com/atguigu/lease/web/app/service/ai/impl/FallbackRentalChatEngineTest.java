package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.web.app.service.ai.RentalChatEngine.ChatExecution;
import com.atguigu.lease.web.app.tools.RoomSearchTool;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackRentalChatEngineTest {

    @Test
    void returnsRoomsAndKnowledgeWithoutCreatingAppointments() {
        RoomSearchTool roomSearch = mock(RoomSearchTool.class);
        LocalRentalKnowledgeService knowledge = mock(LocalRentalKnowledgeService.class);
        when(roomSearch.searchRooms(any(), any(), any(), eq(new BigDecimal("2500"))))
                .thenReturn(List.of(new RoomSearchTool.RoomHit(
                        930001L, "27公寓张江店", "A101", new BigDecimal("2300"), 920001L)));
        when(knowledge.search("预算2500并说明押金"))
                .thenReturn(List.of(new LocalRentalKnowledgeService.KnowledgeSection(
                        "押金与付款", "签约时按合同约定支付押金。", "rag-knowledge.md")));
        var engine = new FallbackRentalChatEngine(roomSearch, knowledge);
        List<ChatSseEvent> events = new ArrayList<>();

        engine.chat(new ChatExecution(7L, "conv-1", "预算2500并说明押金", List.of()), events::add);

        verify(roomSearch).searchRooms(null, null, null, new BigDecimal("2500"));
        assertThat(events).extracting(ChatSseEvent::getType)
                .containsExactly("meta", "message", "recommendations", "citations", "done");
        assertThat((List<?>) event(events, "recommendations").getPayload()).isNotEmpty();
        assertThat((List<?>) event(events, "citations").getPayload()).isNotEmpty();
    }

    private ChatSseEvent event(List<ChatSseEvent> events, String type) {
        return events.stream().filter(event -> type.equals(event.getType())).findFirst().orElseThrow();
    }
}
