package com.atguigu.lease.web.app.service.ai.agent;

import java.util.List;

public record AgentContext(
        Long userId,
        String conversationId,
        String message,
        List<String> history,
        AgentGoal goal,
        int maxSteps) {

    public AgentContext {
        if (userId == null) {
            throw new IllegalArgumentException("Agent user id is required");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Agent conversation id is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Agent message is required");
        }
        history = history == null ? List.of() : List.copyOf(history);
        goal = goal == null ? AgentGoal.ANSWER : goal;
        if (maxSteps < 1) {
            throw new IllegalArgumentException("Agent max steps must be positive");
        }
    }
}
