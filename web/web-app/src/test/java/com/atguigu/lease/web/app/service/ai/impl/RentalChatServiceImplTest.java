package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.common.utils.CacheUtil;
import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
import com.atguigu.lease.web.app.vo.ai.AiChatMetaVo;
import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RentalChatServiceImplTest {

    @Test
    void modelEngineKeepsNumberedContextAndRoomCitations() {
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.ai.chat.client.ChatClient> chatClients = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.ai.vectorstore.VectorStore> vectorStores = mock(ObjectProvider.class);
        var engine = new ModelRentalChatEngine(chatClients, vectorStores, new RagProperties());
        Document room = new Document("27公寓A101 月租2300",
                Map.of("namespace", "rooms", "roomRef", 930001L, "source", "27公寓张江店"));
        Document policy = new Document("押一付一", Map.of("namespace", "doc"));

        assertThat(engine.buildContext(List.of(room, policy))).contains("[1]", "[2]", "27公寓A101", "押一付一");
        assertThat(engine.toCitations(List.of(room, policy)))
                .singleElement()
                .satisfies(citation -> {
                    assertThat(citation.getRoomId()).isEqualTo(930001L);
                    assertThat(citation.getApartment()).isEqualTo("27公寓张江店");
                    assertThat(citation.getSource()).isEqualTo("rooms");
                });
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
        var service = new RentalChatServiceImpl(null, fallback, new RagProperties(), mock(CacheUtil.class), Runnable::run);
        List<ChatSseEvent> events = new ArrayList<>();

        service.chat(7L, request("conv-1", "预算2500并说明押金"), events::add);

        assertThat(events).extracting(ChatSseEvent::getType)
                .containsExactly("meta", "recommendations", "citations", "done");
        assertThat((AiChatMetaVo) events.getFirst().getPayload()).extracting(AiChatMetaVo::getMode)
                .isEqualTo("FALLBACK");
    }

    @Test
    void sameConversationIdUsesDifferentRedisKeysForDifferentUsers() {
        var service = new RentalChatServiceImpl(null, mock(RentalChatEngine.class),
                new RagProperties(), mock(CacheUtil.class), Runnable::run);

        assertThat(service.historyKey(1L, "same")).isNotEqualTo(service.historyKey(2L, "same"));
    }

    @Test
    void rejectsUnsafeConversationId() {
        var service = new RentalChatServiceImpl(null, mock(RentalChatEngine.class),
                new RagProperties(), mock(CacheUtil.class), Runnable::run);

        assertThatThrownBy(() -> service.chat(1L, request("../../shared", "hello"), event -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ChatRequestVo request(String conversationId, String message) {
        var request = new ChatRequestVo();
        request.setConversationId(conversationId);
        request.setMessage(message);
        return request;
    }
}
