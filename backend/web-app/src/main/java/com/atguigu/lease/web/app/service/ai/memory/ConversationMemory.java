package com.atguigu.lease.web.app.service.ai.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned Redis payload for one user conversation.
 */
public record ConversationMemory(
        int schemaVersion,
        long revision,
        String summary,
        Map<String, String> state,
        List<ConversationMessage> recentMessages) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public ConversationMemory {
        summary = summary == null ? "" : summary;
        state = state == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(state));
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

    public static ConversationMemory empty() {
        return new ConversationMemory(CURRENT_SCHEMA_VERSION, 0, "", Map.of(), List.of());
    }

    public record ConversationMessage(String role, String content) {
        public ConversationMessage {
            role = role == null ? "unknown" : role;
            content = content == null ? "" : content;
        }
    }
}
