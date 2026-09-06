package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.common.constant.AiRedisConstant;
import com.atguigu.lease.common.login.LoginUserHolder;
import com.atguigu.lease.common.utils.CacheUtil;
import com.atguigu.lease.common.utils.JsonUtil;
import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.RentalChatEngine;
import com.atguigu.lease.web.app.service.ai.RentalChatService;
import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
public class RentalChatServiceImpl implements RentalChatService {

    private static final Pattern CONVERSATION_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RentalChatServiceImpl.class);

    private final RentalChatEngine modelEngine;
    private final RentalChatEngine fallbackEngine;
    private final RagProperties ragProperties;
    private final CacheUtil cacheUtil;
    private final Executor executor;

    public RentalChatServiceImpl(
            @Qualifier("modelRentalChatEngine") RentalChatEngine modelEngine,
            @Qualifier("fallbackRentalChatEngine") RentalChatEngine fallbackEngine,
            RagProperties ragProperties,
            CacheUtil cacheUtil,
            @Qualifier("applicationTaskExecutor") Executor executor) {
        this.modelEngine = modelEngine;
        this.fallbackEngine = fallbackEngine;
        this.ragProperties = ragProperties;
        this.cacheUtil = cacheUtil;
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
        List<String> history = loadHistory(userId, conversationId);
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
        saveHistory(userId, conversationId, request.getMessage(), answer.toString());
    }

    public String historyKey(Long userId, String conversationId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
        return AiRedisConstant.CHAT_HISTORY_PREFIX + userId + ":" + normalizeConversationId(conversationId);
    }

    private String normalizeConversationId(String conversationId) {
        String normalized = conversationId == null || conversationId.isBlank() ? "default" : conversationId;
        if (!CONVERSATION_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Conversation id contains unsupported characters");
        }
        return normalized;
    }

    private List<String> loadHistory(Long userId, String conversationId) {
        try {
            return readHistory(userId, conversationId);
        } catch (RuntimeException error) {
            LOG.warn("Chat history unavailable: {}", error.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<String> readHistory(Long userId, String conversationId) {
        String raw = cacheUtil.get(historyKey(userId, conversationId), String.class);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (raw.startsWith("[")) return List.of(JsonUtil.parseObject(raw, String[].class));
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void saveHistory(Long userId, String conversationId, String userMessage, String answer) {
        List<String> lines = new ArrayList<>(loadHistory(userId, conversationId));
        lines.add("用户:" + userMessage);
        lines.add("顾问:" + answer);
        int maxLines = Math.max(2, ragProperties.getHistoryTurns() * 2);
        while (lines.size() > maxLines) {
            lines.removeFirst();
        }
        try {
            cacheUtil.set(
                historyKey(userId, conversationId),
                JsonUtil.toJsonString(lines),
                AiRedisConstant.CHAT_HISTORY_TTL_SEC,
                TimeUnit.SECONDS);
        } catch (RuntimeException error) {
            // History is optional. Never rerun tools or fail a completed appointment draft due to Redis.
            LOG.warn("Chat history save failed: {}", error.getClass().getSimpleName());
        }
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
