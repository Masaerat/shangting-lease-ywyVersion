package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.common.login.LoginUserHolder;
import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
import com.atguigu.lease.web.app.service.ai.RentalChatService;
import com.atguigu.lease.web.app.service.ai.memory.ConversationMemoryService;
import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
public class RentalChatServiceImpl implements RentalChatService {

    private static final Pattern CONVERSATION_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private final RentalChatEngine modelEngine;
    private final RentalChatEngine fallbackEngine;
    private final ConversationMemoryService memoryService;
    private final Executor executor;

    public RentalChatServiceImpl(
            @Qualifier("modelRentalChatEngine") RentalChatEngine modelEngine,
            @Qualifier("fallbackRentalChatEngine") RentalChatEngine fallbackEngine,
            ConversationMemoryService memoryService,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.modelEngine = modelEngine;
        this.fallbackEngine = fallbackEngine;
        this.memoryService = memoryService;
        this.executor = executor;
    }

    @Override
    public void chat(ChatRequestVo request, SseEmitter emitter) {
        Long userId = currentUserId();
        executor.execute(() -> {
            try {
                chat(userId, request, event -> send(emitter, event));
                emitter.complete();
            } catch (Exception error) {
                send(emitter, new ChatSseEvent("error", message(error)));
                emitter.completeWithError(error);
            }
        });
    }

    public void chat(Long userId, ChatRequestVo request, Consumer<ChatSseEvent> sink) {
        if (userId == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("Chat message is required");
        }
        if (request.getMessage().length() > 4000) throw new IllegalArgumentException("Chat message is too long");
        String conversationId = normalizeConversationId(request.getConversationId());
        List<String> history = memoryService.loadContext(userId, conversationId);
        RentalChatEngine.ChatExecution execution = new RentalChatEngine.ChatExecution(
                userId, conversationId, request.getMessage(), history);
        StringBuilder answer = new StringBuilder();
        Consumer<ChatSseEvent> capturingSink = event -> {
            if ("message".equals(event.getType()) && event.getPayload() != null) {
                answer.append(event.getPayload());
            }
            sink.accept(event);
        };

        boolean modelSucceeded = false;
        List<ChatSseEvent> buffered = new ArrayList<>();
        if (modelEngine != null && modelEngine.available()) {
            try {
                modelEngine.chat(execution, buffered::add);
                modelSucceeded = true;
            } catch (RuntimeException ignored) {
                answer.setLength(0);
            }
        }
        if (modelSucceeded) buffered.forEach(capturingSink);
        if (!modelSucceeded) {
            fallbackEngine.chat(execution, capturingSink);
        }
        memoryService.append(userId, conversationId, request.getMessage(), answer.toString());
    }

    public String historyKey(Long userId, String conversationId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
        return memoryService.key(userId, normalizeConversationId(conversationId));
    }

    private String normalizeConversationId(String conversationId) {
        String normalized = conversationId == null || conversationId.isBlank() ? "default" : conversationId;
        if (!CONVERSATION_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Conversation id contains unsupported characters");
        }
        return normalized;
    }

    private void send(SseEmitter emitter, ChatSseEvent event) {
        try {
            emitter.send(SseEmitter.event().name("chat").data(event));
        } catch (Exception ignored) {
            // The client may have disconnected while an asynchronous response was in flight.
        }
    }

    private Long currentUserId() {
        return LoginUserHolder.getLoginUser() == null ? null : LoginUserHolder.getLoginUser().getUserId();
    }

    private String message(Exception error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }
}
