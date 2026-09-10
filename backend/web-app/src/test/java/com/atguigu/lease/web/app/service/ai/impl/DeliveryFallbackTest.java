package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.notification.AppointmentNotificationStore;
import com.atguigu.lease.notification.AppointmentDeliveryStatus;
import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
import com.atguigu.lease.web.app.service.ai.rag.RentalKnowledgeService;
import com.atguigu.lease.web.app.tools.RoomSearchTool;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryFallbackTest {
    private final RoomSearchTool rooms = mock(RoomSearchTool.class);
    private final RentalKnowledgeService rag = mock(RentalKnowledgeService.class);
    private final AppointmentNotificationStore store = mock(AppointmentNotificationStore.class);
    private final FallbackRentalChatEngine engine = new FallbackRentalChatEngine(rooms, rag, store);

    private List<ChatSseEvent> chat(String message) {
        List<ChatSseEvent> events = new ArrayList<>();
        engine.chat(new RentalChatEngine.ChatExecution(7L, "test", message, List.of()), events::add);
        verifyNoInteractions(rooms, rag);
        return events;
    }

    @Test
    void readsStatusWithoutModelOrPolicySearch() {
        when(store.status(7L, 20L)).thenReturn(new AppointmentDeliveryStatus(20L, 100L, 1, null, "PUBLISHED", null));
        var events = chat("查询预约20的状态");
        assertThat(events).extracting(ChatSseEvent::getType).containsExactly("meta", "message", "appointment_status", "done");
        assertThat(events.get(1).getPayload().toString()).contains("不是送达成功");
    }

    @Test
    void asksForAppointmentIdInsteadOfGuessingFromRoomId() {
        assertThat(chat("房间100的预约成功了吗").get(1).getPayload().toString()).contains("请提供");
        verifyNoInteractions(store);
    }

    @Test
    void notificationQueryAndMissingAppointmentDoNotInventResults() {
        when(store.list(7L, 20)).thenReturn(List.of());
        assertThat(chat("我的通知").get(1).getPayload().toString()).contains("不代表您没有预约");
        assertThat(chat("预约999的状态").get(1).getPayload()).isEqualTo("未找到该预约。");
    }
}
