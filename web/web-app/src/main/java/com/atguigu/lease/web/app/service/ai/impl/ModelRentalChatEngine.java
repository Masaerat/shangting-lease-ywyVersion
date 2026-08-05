package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
import com.atguigu.lease.web.app.vo.ai.AiChatMetaVo;
import com.atguigu.lease.web.app.vo.ai.AiRecommendationVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import com.atguigu.lease.web.app.vo.ai.RoomCitationVo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service("modelRentalChatEngine")
public class ModelRentalChatEngine implements RentalChatEngine {

    private static final String ROOMS_NAMESPACE = "rooms";

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final RagProperties ragProperties;

    public ModelRentalChatEngine(ObjectProvider<ChatClient> chatClientProvider,
                                 ObjectProvider<VectorStore> vectorStoreProvider,
                                 RagProperties ragProperties) {
        this.chatClientProvider = chatClientProvider;
        this.vectorStoreProvider = vectorStoreProvider;
        this.ragProperties = ragProperties;
    }

    @Override
    public String mode() {
        return "MODEL";
    }

    @Override
    public boolean available() {
        return chatClientProvider.getIfAvailable() != null && vectorStoreProvider.getIfAvailable() != null;
    }

    @Override
    public void chat(ChatExecution execution, Consumer<ChatSseEvent> sink) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (chatClient == null || vectorStore == null) {
            throw new IllegalStateException("AI model or vector store is unavailable");
        }

        List<Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
                .query(execution.message())
                .topK(ragProperties.getTopK())
                .similarityThreshold(ragProperties.getSimilarityThreshold())
                .build());
        String prompt = prompt(execution, buildContext(retrieved));
        List<String> tokens = chatClient.prompt().user(prompt).stream().content().collectList().block();

        sink.accept(new ChatSseEvent("meta", new AiChatMetaVo(mode(), execution.conversationId())));
        if (tokens != null) {
            tokens.forEach(token -> sink.accept(new ChatSseEvent("message", token)));
        }
        sink.accept(new ChatSseEvent("recommendations", toRecommendations(retrieved)));
        sink.accept(new ChatSseEvent("citations", toCitations(retrieved)));
        sink.accept(new ChatSseEvent("done", null));
    }

    public String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            context.append('[').append(i + 1).append("] ")
                    .append(documents.get(i).getText()).append('\n');
        }
        return context.toString();
    }

    public List<RoomCitationVo> toCitations(List<Document> documents) {
        if (documents == null) {
            return List.of();
        }
        List<RoomCitationVo> citations = new ArrayList<>();
        for (Document document : documents) {
            if (!ROOMS_NAMESPACE.equals(document.getMetadata().get("namespace"))) {
                continue;
            }
            Object roomRef = document.getMetadata().get("roomRef");
            citations.add(new RoomCitationVo(
                    longValue(roomRef),
                    stringValue(document, "source"),
                    stringValue(document, "roomNumber"),
                    decimalValue(document.getMetadata().get("rent")),
                    ROOMS_NAMESPACE));
        }
        return citations;
    }

    private List<AiRecommendationVo> toRecommendations(List<Document> documents) {
        return toCitations(documents).stream()
                .map(citation -> new AiRecommendationVo(
                        citation.getRoomId(), null, citation.getApartment(),
                        citation.getRoomNumber(), citation.getRent()))
                .toList();
    }

    private String prompt(ChatExecution execution, String context) {
        StringBuilder prompt = new StringBuilder();
        if (!context.isBlank()) {
            prompt.append("参考资料:\n").append(context).append("\n");
        }
        execution.history().forEach(line -> prompt.append(line).append('\n'));
        return prompt.append("用户:").append(execution.message()).toString();
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private BigDecimal decimalValue(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private String stringValue(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }
}
