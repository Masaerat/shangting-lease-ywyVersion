package com.atguigu.lease.web.app.service.ai.agent;

import com.atguigu.lease.config.ai.AiAgentProperties;
import com.atguigu.lease.web.app.tools.AgentRoomSearchTool;
import com.atguigu.lease.web.app.tools.AppointmentDraftTool;
import com.atguigu.lease.web.app.tools.MoveInCostTool;
import com.atguigu.lease.web.app.tools.RentalKnowledgeTool;
import com.atguigu.lease.web.app.tools.RoomDetailTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, RegisteredTool> tools;
    private final PermissionPolicy permissionPolicy;
    private final AiAgentProperties properties;
    private final AgentTaskExecutor executor;

    public ToolRegistry(AgentRoomSearchTool roomSearchTool,
                        RoomDetailTool roomDetailTool,
                        MoveInCostTool moveInCostTool,
                        RentalKnowledgeTool knowledgeTool,
                        AppointmentDraftTool appointmentDraftTool,
                        PermissionPolicy permissionPolicy,
                        AiAgentProperties properties,
                        AgentTaskExecutor executor) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(roomSearchTool, roomDetailTool, moveInCostTool, knowledgeTool, appointmentDraftTool)
                .build().getToolCallbacks();
        Map<String, AgentPermission> permissions = Map.of(
                "search_available_rooms", AgentPermission.READ,
                "get_room_detail", AgentPermission.READ,
                "calculate_move_in_cost", AgentPermission.READ,
                "search_rental_knowledge", AgentPermission.READ,
                "create_appointment_draft", AgentPermission.PREPARE);
        Map<String, RegisteredTool> registered = new LinkedHashMap<>();
        Arrays.stream(callbacks).forEach(callback -> {
            String name = callback.getToolDefinition().name();
            AgentPermission permission = permissions.get(name);
            if (permission == null) {
                throw new IllegalStateException("Tool permission is not declared: " + name);
            }
            registered.put(name, new RegisteredTool(name, permission, callback));
        });
        this.tools = Map.copyOf(registered);
        this.permissionPolicy = permissionPolicy;
        this.properties = properties;
        this.executor = executor;
    }

    public List<ToolCallback> callbacks() {
        return tools.values().stream()
                .map(tool -> (ToolCallback) new GuardedToolCallback(tool))
                .toList();
    }

    public List<String> names() {
        return tools.keySet().stream().sorted().toList();
    }

    public void validate(List<AssistantMessage.ToolCall> calls,
                         AgentContext context,
                         AgentExecutionState state) {
        for (AssistantMessage.ToolCall call : calls) {
            RegisteredTool tool = tools.get(call.name());
            if (tool == null) {
                throw new AgentExecutionException("Unknown tool requested: " + call.name(), "UNKNOWN_TOOL");
            }
            permissionPolicy.requireAllowed(tool.name(), tool.permission(), context);
            state.registerToolRequest(tool.name(), properties.getMaxSameToolCalls());
        }
    }

    private record RegisteredTool(String name, AgentPermission permission, ToolCallback callback) {
    }

    private final class GuardedToolCallback implements ToolCallback {

        private final RegisteredTool registered;

        private GuardedToolCallback(RegisteredTool registered) {
            this.registered = registered;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return registered.callback().getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return registered.callback().getToolMetadata();
        }

        @Override
        public String call(String input) {
            return registered.callback().call(input);
        }

        @Override
        public String call(String input, ToolContext toolContext) {
            Object contextValue = toolContext.getContext().get("rentalAgentContext");
            if (!(contextValue instanceof AgentContext context)) {
                throw new AgentExecutionException("Agent context is missing", "INVALID_CONTEXT");
            }
            permissionPolicy.requireAllowed(registered.name(), registered.permission(), context);
            AgentExecutionState state = state(toolContext);
            long started = System.nanoTime();
            try {
                String result = executor.call(
                        () -> registered.callback().call(input, toolContext),
                        Duration.ofMillis(properties.getToolTimeoutMs()), "TOOL_TIMEOUT");
                String limited = limit(result);
                state.recordStep(new AgentStep(
                        state.nextStepNumber(), null, registered.name(), "SUCCESS",
                        elapsedMs(started), observationCount(state, registered.name()), null));
                return limited;
            } catch (RuntimeException error) {
                state.recordStep(new AgentStep(
                        state.nextStepNumber(), null, registered.name(), "ERROR",
                        elapsedMs(started), null, errorType(error)));
                throw error;
            }
        }

        private AgentExecutionState state(ToolContext context) {
            Object value = context.getContext().get(AgentExecutionState.CONTEXT_KEY);
            if (value instanceof AgentExecutionState state) {
                return state;
            }
            throw new AgentExecutionException("Agent execution state is missing", "INVALID_CONTEXT");
        }

        private int observationCount(AgentExecutionState state, String tool) {
            return (int) state.observations().stream().filter(item -> tool.equals(item.tool())).count();
        }

        private String limit(String value) {
            if (value == null || value.length() <= properties.getMaxToolResultChars()) {
                return value;
            }
            return value.substring(0, properties.getMaxToolResultChars()) + "\n[tool result truncated]";
        }

        private long elapsedMs(long started) {
            return (System.nanoTime() - started) / 1_000_000;
        }

        private String errorType(RuntimeException error) {
            return error instanceof AgentExecutionException agentError
                    ? agentError.getErrorType() : error.getClass().getSimpleName();
        }
    }
}
