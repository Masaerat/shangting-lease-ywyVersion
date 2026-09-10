package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.web.app.service.ai.agent.AgentExecutionState;
import org.springframework.ai.chat.model.ToolContext;

final class AgentToolSupport {

    private AgentToolSupport() {
    }

    static AgentExecutionState state(ToolContext context) {
        Object value = context.getContext().get(AgentExecutionState.CONTEXT_KEY);
        if (value instanceof AgentExecutionState state) {
            return state;
        }
        throw new IllegalStateException("Rental agent execution state is missing");
    }

    static Long userId(ToolContext context) {
        Object value = context.getContext().get(AgentExecutionState.USER_ID_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Rental agent user id is missing");
    }
}
