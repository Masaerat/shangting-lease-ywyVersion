package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.common.utils.CacheUtil;
import com.atguigu.lease.config.ai.AiAgentProperties;
import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
import com.atguigu.lease.web.app.service.ai.agent.AgentObservation;
import com.atguigu.lease.web.app.service.ai.agent.AgentResult;
import com.atguigu.lease.web.app.service.ai.agent.AgentStep;
import com.atguigu.lease.web.app.service.ai.agent.RentalAgentRuntime;
import com.atguigu.lease.web.app.service.ai.memory.ConversationMemoryService;
import com.atguigu.lease.web.app.service.ai.rag.KnowledgeCitation;
import com.atguigu.lease.web.app.service.ai.rag.KnowledgeSearchResult;
import com.atguigu.lease.web.app.tools.RoomSearchTool;
import com.atguigu.lease.web.app.vo.ai.AiChatMetaVo;
import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RentalChatServiceImplTest {

    @Test
    void modelEngineEmitsStructuredToolResultsAndTrajectory() {
        RentalAgentRuntime runtime = mock(RentalAgentRuntime.class);
        when(runtime.available()).thenReturn(true);
        RoomSearchTool.RoomHit room = new RoomSearchTool.RoomHit(
                930001L, "27公寓张江店", "A101", new BigDecimal("2300"), 920001L);
        KnowledgeCitation citation = new KnowledgeCitation(
                "deposit-1", 12L, "租房政策.md", "DEPOSIT", "押金", "退还条件",
                "租房政策.md", 1, "完成结算后按合同退还押金。", 0.9);
        when(runtime.execute(org.mockito.ArgumentMatchers.any())).thenReturn(new AgentResult(
                "已找到房源，并附上押金规则。", "luna", "trace-1",
                List.of(
                        new AgentObservation("search_available_rooms", List.of(room)),
                        new AgentObservation("search_rental_knowledge", new KnowledgeSearchResult(
                                "押金怎么退", "押金怎么退 退还条件", "HYBRID", List.of(citation)))),
                List.of(new AgentStep(1, "luna", "search_available_rooms", "SUCCESS", 5, 1, null))));
        @SuppressWarnings("unchecked")
        ObjectProvider<RentalAgentRuntime> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(runtime);
        ModelRentalChatEngine engine = new ModelRentalChatEngine(provider, new AiAgentProperties());
        List<ChatSseEvent> events = new ArrayList<>();

        engine.chat(new RentalChatEngine.ChatExecution(
                7L, "conv-1", "预算2500元，押金怎么退", List.of()), events::add);

        assertThat(events).extracting(ChatSseEvent::getType)
                .containsExactly("meta", "message", "recommendations", "citations", "trajectory", "done");
        AiChatMetaVo meta = (AiChatMetaVo) events.getFirst().getPayload();
        assertThat(meta.getProvider()).isEqualTo("luna");
        assertThat(meta.getTraceId()).isEqualTo("trace-1");
        assertThat((List<?>) event(events, "recommendations").getPayload()).hasSize(1);
        assertThat((List<?>) event(events, "citations").getPayload()).hasSize(1);
    }

    @Test
    void missingModelUsesFallback() {
        RentalChatEngine fallback = new RentalChatEngine() {
            @Override
            public String mode() {
                return "FALLBACK";
            }

            @Override
            public void chat(ChatExecution execution, java.util.function.Consumer<ChatSseEvent> sink) {
                sink.accept(new ChatSseEvent("meta", new AiChatMetaVo(mode(), execution.conversationId())));
                sink.accept(new ChatSseEvent("recommendations", List.of("room")));
                sink.accept(new ChatSseEvent("citations", List.of("deposit")));
                sink.accept(new ChatSseEvent("done", null));
            }
        };
        CacheUtil cache = mock(CacheUtil.class);
        var service = new RentalChatServiceImpl(null, fallback,
                new ConversationMemoryService(cache, new RagProperties()), Runnable::run);
        List<ChatSseEvent> events = new ArrayList<>();

        service.chat(7L, request("conv-1", "预算2500并说明押金"), events::add);

        assertThat(events).extracting(ChatSseEvent::getType)
                .containsExactly("meta", "recommendations", "citations", "done");
        assertThat((AiChatMetaVo) events.getFirst().getPayload()).extracting(AiChatMetaVo::getMode)
                .isEqualTo("FALLBACK");
    }

    @Test
    void sameConversationIdUsesDifferentRedisKeysForDifferentUsers() {
        CacheUtil cache = mock(CacheUtil.class);
        var service = new RentalChatServiceImpl(null, mock(RentalChatEngine.class),
                new ConversationMemoryService(cache, new RagProperties()), Runnable::run);

        assertThat(service.historyKey(1L, "same")).isNotEqualTo(service.historyKey(2L, "same"));
    }

    @Test
    void rejectsUnsafeConversationId() {
        CacheUtil cache = mock(CacheUtil.class);
        var service = new RentalChatServiceImpl(null, mock(RentalChatEngine.class),
                new ConversationMemoryService(cache, new RagProperties()), Runnable::run);

        assertThatThrownBy(() -> service.chat(1L, request("../../shared", "hello"), event -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ChatSseEvent event(List<ChatSseEvent> events, String type) {
        return events.stream().filter(item -> type.equals(item.getType())).findFirst().orElseThrow();
    }

    @Test
    void modelDraftIsExposedWithItsConfirmationToken() {
        RentalAgentRuntime runtime = mock(RentalAgentRuntime.class);
        when(runtime.available()).thenReturn(true);
        var draft = new com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftResponse(
                "secret-token", java.time.Instant.now().plusSeconds(600), 10L, 20L,
                "用户", "13800000000", java.time.LocalDateTime.now().plusDays(1), null);
        when(runtime.execute(org.mockito.ArgumentMatchers.any())).thenReturn(new AgentResult(
                "请确认草稿", "test", "trace", List.of(new AgentObservation("create_appointment_draft", draft)), List.of()));
        @SuppressWarnings("unchecked") ObjectProvider<RentalAgentRuntime> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(runtime);
        var events = new ArrayList<ChatSseEvent>();
        new ModelRentalChatEngine(provider, new AiAgentProperties()).chat(
                new RentalChatEngine.ChatExecution(7L, "conv", "预约", List.of()), events::add);
        assertThat(event(events, "appointment_draft").getPayload()).isSameAs(draft);
        assertThat(event(events, "done").getPayload().toString()).contains("CONFIRM_APPOINTMENT");
    }

    @Test
    void historyPreservesMultilineMessagesAndCacheFailureDoesNotFailChat() {
        CacheUtil cache = mock(CacheUtil.class);
        RentalChatEngine fallback = mock(RentalChatEngine.class);
        var stored = new java.util.concurrent.atomic.AtomicReference<String>();
        org.mockito.Mockito.doAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return null;
        }).when(cache).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        when(cache.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenAnswer(invocation -> stored.get());
        var service = new RentalChatServiceImpl(null, fallback,
                new ConversationMemoryService(cache, new RagProperties()), Runnable::run);
        service.chat(7L, request("conv", "第一行\n第二行"), e -> {});
        service.chat(7L, request("conv", "继续"), e -> {});
        var execution = org.mockito.ArgumentCaptor.forClass(RentalChatEngine.ChatExecution.class);
        org.mockito.Mockito.verify(fallback, org.mockito.Mockito.times(2)).chat(execution.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(execution.getAllValues().get(1).history()).containsExactly("用户:第一行\n第二行", "顾问:");

        when(cache.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(new IllegalStateException("redis down"));
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down")).when(cache).set(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThatCode(() -> service.chat(7L, request("conv", "继续"), e -> {})).doesNotThrowAnyException();
    }

    @Test
    void legacyHistoryRemainsReadable() {
        CacheUtil cache = mock(CacheUtil.class);
        when(cache.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("用户:预算2500\n顾问:已找到");
        RentalChatEngine fallback = mock(RentalChatEngine.class);
        new RentalChatServiceImpl(null, fallback,
                new ConversationMemoryService(cache, new RagProperties()), Runnable::run)
                .chat(7L, request("conv", "继续"), e -> {});
        var execution = org.mockito.ArgumentCaptor.forClass(RentalChatEngine.ChatExecution.class);
        org.mockito.Mockito.verify(fallback).chat(execution.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(execution.getValue().history())
                .anyMatch(line -> line.startsWith("已确认用户条件") && line.contains("2500"))
                .containsSubsequence("用户:预算2500", "顾问:已找到");
    }

    private ChatRequestVo request(String conversationId, String message) {
        var request = new ChatRequestVo();
        request.setConversationId(conversationId);
        request.setMessage(message);
        return request;
    }
}
