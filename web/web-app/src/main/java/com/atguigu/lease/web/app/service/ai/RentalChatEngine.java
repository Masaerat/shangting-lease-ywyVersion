package com.atguigu.lease.web.app.service.ai;

import com.atguigu.lease.web.app.vo.ai.ChatSseEvent;

import java.util.List;
import java.util.function.Consumer;

public interface RentalChatEngine {

    String mode();

    default boolean available() {
        return true;
    }

    void chat(ChatExecution execution, Consumer<ChatSseEvent> sink);

    record ChatExecution(
            Long userId,
            String conversationId,
            String message,
            List<String> history) {
    }
}
