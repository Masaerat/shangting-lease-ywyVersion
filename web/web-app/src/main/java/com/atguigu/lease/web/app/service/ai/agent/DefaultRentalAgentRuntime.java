package com.atguigu.lease.web.app.service.ai.agent;

import com.atguigu.lease.config.ai.AiAgentProperties;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DefaultRentalAgentRuntime implements RentalAgentRuntime {

    private static final String SYSTEM_PROMPT = """
            你是 27 公寓的租房业务 Agent。你必须通过工具获取事实，再组织简洁、可核验的中文回答。
            规则：
            1. 房源、价格、付款方式和可租状态只能来自业务工具，不能编造。
            2. 押金、付款、看房、维修和退租规则必须调用 search_rental_knowledge，并引用检索结果。
            3. 同时包含预算和政策的问题，需要分别调用 search_available_rooms 与 search_rental_knowledge。
            4. 只有用户提供完整房间、未来时间、姓名和手机号时，才能调用 create_appointment_draft。
            5. 草稿不是正式预约。你没有确认预约、签约、付款或执行任意 SQL 的权限。
            6. 工具无结果或失败时必须如实说明，不得补造数据。
            7. 查询预约进度调用 get_appointment_status；预约编号必须来自确认结果或用户明确提供，不可把房间编号当作预约编号。
            8. 查询通知调用 list_my_notifications。PUBLISHED 仅表示 MQ 接收，DELIVERED 仅表示站内通知已落库，绝不声称短信或邮件送达。
            9. 预约状态问题不是预约政策问题，不必为状态查询检索政策；缺少预约编号先询问。
            10. 上下文中的“已确认用户条件”来自用户历史表达，可用于理解偏好，但房源价格、库存和业务状态仍必须通过工具核验；本轮用户的新表述优先。
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ToolRegistry toolRegistry;
    private final AiAgentProperties properties;
    private final AgentTaskExecutor executor;
    private final ToolCallingManager toolCallingManager;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultRentalAgentRuntime(ObjectProvider<ChatModel> chatModelProvider,
                                     ToolRegistry toolRegistry,
                                     AiAgentProperties properties,
                                     AgentTaskExecutor executor) {
        this(chatModelProvider, toolRegistry, properties, executor, ToolCallingManager.builder().build());
    }

    DefaultRentalAgentRuntime(ObjectProvider<ChatModel> chatModelProvider,
                              ToolRegistry toolRegistry,
                              AiAgentProperties properties,
                              AgentTaskExecutor executor,
                              ToolCallingManager toolCallingManager) {
        this.chatModelProvider = chatModelProvider;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.executor = executor;
        this.toolCallingManager = toolCallingManager;
    }

    @Override
    public boolean available() {
        return chatModelProvider.getIfAvailable() != null;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new AgentExecutionException("AI model is unavailable", "MODEL_UNAVAILABLE");
        }
        String primary = blankToNull(properties.getPrimaryModel());
        long deadline = System.nanoTime() + Duration.ofMillis(properties.getTimeoutMs()).toNanos();
        AgentExecutionState state = new AgentExecutionState(deadline);
        try {
            return executeWithModel(chatModel, context, primary, state, deadline);
        } catch (RuntimeException primaryFailure) {
            if (state.hasToolRequests()) return partial(primary, state, primaryFailure);
            String fallback = blankToNull(properties.getFallbackModel());
            if (fallback == null || fallback.equals(primary)) {
                throw primaryFailure;
            }
            ensureBeforeDeadline(deadline);
            try {
                return executeWithModel(chatModel, context, fallback, state, deadline);
            } catch (RuntimeException fallbackFailure) {
                if (state.hasToolRequests()) return partial(fallback, state, fallbackFailure);
                throw fallbackFailure;
            }
        }
    }

    private AgentResult partial(String model, AgentExecutionState state, RuntimeException error) {
        state.recordStep(new AgentStep(state.nextStepNumber(), displayModel(model), null, "PARTIAL", 0, null,
                error instanceof AgentExecutionException agentError ? agentError.getErrorType() : error.getClass().getSimpleName()));
        return result("本轮工具处理或回答未完整完成，请查看已返回的结构化结果。若有预约草稿，仍需您显式确认；本轮不会自动确认预约。", model, state);
    }

    private AgentResult executeWithModel(ChatModel chatModel, AgentContext context, String model,
                                        AgentExecutionState state, long deadline) {
        ToolCallingChatOptions.Builder optionsBuilder = ToolCallingChatOptions.builder()
                .toolCallbacks(toolRegistry.callbacks())
                .internalToolExecutionEnabled(false)
                .toolContext(Map.of(
                        AgentExecutionState.CONTEXT_KEY, state,
                        AgentExecutionState.USER_ID_KEY, context.userId(),
                        "rentalAgentContext", context))
                .temperature(0.1);
        if (model != null) {
            optionsBuilder.model(model);
        }
        ToolCallingChatOptions options = optionsBuilder.build();
        Prompt prompt = new Prompt(messages(context), options);
        String answer = "";

        for (int step = 1; step <= Math.min(context.maxSteps(), properties.getMaxSteps()); step++) {
            state.beginModelStep(Math.min(context.maxSteps(), properties.getMaxSteps()));
            long started = System.nanoTime();
            ChatResponse response = callModel(chatModel, prompt, deadline);
            AssistantMessage output = response.getResult().getOutput();
            answer = output.getText() == null ? "" : output.getText();
            state.recordStep(new AgentStep(
                    state.nextStepNumber(), displayModel(model), null,
                    response.hasToolCalls() ? "TOOL_REQUEST" : "COMPLETED",
                    elapsedMs(started), output.getToolCalls().size(), null));
            if (!response.hasToolCalls()) {
                return result(answer, model, state);
            }
            toolRegistry.validate(output.getToolCalls(), context, state);
            Prompt toolPrompt = prompt;
            ensureBeforeDeadline(deadline);
            ToolExecutionResult toolResult = executor.call(() -> toolCallingManager.executeToolCalls(toolPrompt, response),
                    Duration.ofNanos(deadline - System.nanoTime()), "AGENT_TIMEOUT");
            if (toolResult.returnDirect()) {
                return result(answer, model, state);
            }
            prompt = new Prompt(toolResult.conversationHistory(), options);
            ensureBeforeDeadline(deadline);
        }
        throw new AgentExecutionException("Agent step limit exceeded", "STEP_LIMIT");
    }

    private ChatResponse callModel(ChatModel chatModel, Prompt prompt, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new AgentExecutionException("Agent request timed out", "AGENT_TIMEOUT");
        }
        return executor.call(() -> chatModel.call(prompt), Duration.ofNanos(remaining), "MODEL_TIMEOUT");
    }

    private List<Message> messages(AgentContext context) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        StringBuilder user = new StringBuilder();
        if (!context.history().isEmpty()) {
            user.append("分层对话记忆：\n");
            context.history().forEach(line -> user.append(line).append('\n'));
        }
        user.append("本轮目标：").append(context.goal()).append('\n');
        user.append("用户：").append(context.message());
        messages.add(new UserMessage(user.toString()));
        return messages;
    }

    private AgentResult result(String answer, String model, AgentExecutionState state) {
        return new AgentResult(
                answer, displayModel(model), state.traceId(), state.observations(),
                properties.isTrajectoryEnabled() ? state.trajectory() : List.of());
    }

    private void ensureBeforeDeadline(long deadline) {
        if (System.nanoTime() >= deadline) {
            throw new AgentExecutionException("Agent request timed out", "AGENT_TIMEOUT");
        }
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String displayModel(String model) {
        return model == null ? "configured-default" : model;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
