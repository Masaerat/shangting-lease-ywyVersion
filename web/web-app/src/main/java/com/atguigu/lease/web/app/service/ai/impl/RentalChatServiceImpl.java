package com.atguigu.lease.web.app.service.ai.impl;

import com.atguigu.lease.common.constant.AiRedisConstant;
import com.atguigu.lease.common.login.LoginUserHolder;
import com.atguigu.lease.common.utils.CacheUtil;
import com.atguigu.lease.config.ai.RagProperties;
import com.atguigu.lease.web.app.service.ai.RentalChatService;
import com.atguigu.lease.web.app.vo.ai.ChatRequestVo;
import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;
import com.atguigu.lease.web.app.vo.ai.RoomCitationVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class RentalChatServiceImpl implements RentalChatService {

    private static final String NS_ROOMS = "rooms";

    private final ChatClient rentalChatClient;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final CacheUtil cacheUtil;
    private final Executor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RentalChatServiceImpl(ChatClient rentalChatClient, VectorStore vectorStore,
                                 RagProperties ragProperties, CacheUtil cacheUtil,
                                 @Qualifier("applicationTaskExecutor") Executor executor) {
        this.rentalChatClient = rentalChatClient;
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
        this.cacheUtil = cacheUtil;
        this.executor = executor;
    }

    @Override
    public void chat(ChatRequestVo request, SseEmitter emitter) {
        executor.execute(() -> {
            try {
                String conversationId = request.getConversationId();
                if (conversationId == null || conversationId.isBlank()) {
                    Long uid = currentUserId();
                    conversationId = "u-" + (uid == null ? "anon" : uid);
                }
                final String convId = conversationId;

                // 1. 取历史
                List<String> history = loadHistory(convId);

                // 2. 手动检索(混合来源:rooms + doc)
                List<Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(request.getMessage())
                        .topK(ragProperties.getTopK())
                        .similarityThreshold(ragProperties.getSimilarityThreshold())
                        .build());

                // 3. 拼上下文 + 提问
                String context = buildContext(retrieved);
                StringBuilder promptBuilder = new StringBuilder();
                if (!context.isBlank()) promptBuilder.append("参考资料:\n").append(context).append("\n\n");
                for (String h : history) promptBuilder.append(h).append('\n');
                promptBuilder.append("用户:").append(request.getMessage());

                // 4. 流式回答
                StringBuilder answer = new StringBuilder();
                rentalChatClient.prompt().user(promptBuilder.toString()).stream().content().subscribe(
                        token -> { answer.append(token); send(emitter, "message", token); },
                        err -> send(emitter, "error", err.getMessage() == null ? err.toString() : err.getMessage()),
                        () -> {
                            send(emitter, "done", toCitations(retrieved));
                            emitter.complete();
                            saveHistory(convId, request.getMessage(), answer.toString());
                        });
            } catch (Exception e) {
                send(emitter, "error", e.getMessage() == null ? e.toString() : e.getMessage());
                emitter.completeWithError(e);
            }
        });
    }

    /** 把检索文档转成上下文文本(纯函数,便于测试)。 */
    public String buildContext(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(docs.get(i).getText()).append('\n');
        }
        return sb.toString();
    }

    /** 把 namespace=rooms 的检索结果转成引用(纯函数,便于测试)。 */
    public List<RoomCitationVo> toCitations(List<Document> docs) {
        if (docs == null) return List.of();
        List<RoomCitationVo> list = new ArrayList<>();
        for (Document d : docs) {
            if (!NS_ROOMS.equals(d.getMetadata().get("namespace"))) continue;
            Object ref = d.getMetadata().get("roomRef");
            list.add(new RoomCitationVo(
                    ref == null ? null : Long.valueOf(ref.toString()),
                    (String) d.getMetadata().get("source"),
                    null, null, NS_ROOMS));
        }
        return list;
    }

    private void send(SseEmitter emitter, String type, Object payload) {
        try {
            emitter.send(SseEmitter.event().name("chat").data(new ChatSseEvent(type, payload)));
        } catch (Exception ignored) { /* 客户端可能已断开 */ }
    }

    /** 历史以换行分隔的字符串存储;返回最近若干行。 */
    private List<String> loadHistory(String conversationId) {
        String raw = cacheUtil.get(historyKey(conversationId), String.class);
        if (raw == null || raw.isBlank()) return List.of();
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (!line.isBlank()) lines.add(line);
        }
        return lines;
    }

    private void saveHistory(String conversationId, String userMsg, String answer) {
        List<String> lines = new ArrayList<>(loadHistory(conversationId));
        lines.add("用户:" + userMsg);
        lines.add("顾问:" + answer);
        // 保留最近 historyTurns 轮(每轮 2 行)
        int maxLines = Math.max(2, ragProperties.getHistoryTurns() * 2);
        while (lines.size() > maxLines) lines.remove(0);
        cacheUtil.set(historyKey(conversationId), String.join("\n", lines),
                AiRedisConstant.CHAT_HISTORY_TTL_SEC, TimeUnit.SECONDS);
    }

    private String historyKey(String conversationId) {
        return AiRedisConstant.CHAT_HISTORY_PREFIX + conversationId;
    }

    private Long currentUserId() {
        try {
            return LoginUserHolder.getLoginUser() == null ? null : LoginUserHolder.getLoginUser().getUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
