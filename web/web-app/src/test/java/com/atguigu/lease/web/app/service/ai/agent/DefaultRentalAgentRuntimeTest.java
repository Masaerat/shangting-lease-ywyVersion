package com.atguigu.lease.web.app.service.ai.agent;

import com.atguigu.lease.config.ai.AiAgentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultRentalAgentRuntimeTest {

    private final AgentTaskExecutor executor = new AgentTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void returnsFinalAnswerWhenModelDoesNotRequestTools() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("可以为你找房", List.of()));
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.callbacks()).thenReturn(List.of());
        DefaultRentalAgentRuntime runtime = runtime(model, registry, mock(ToolCallingManager.class));

        AgentResult result = runtime.execute(context(3));

        assertThat(result.answer()).isEqualTo("可以为你找房");
        assertThat(result.traceId()).isNotBlank();
        assertThat(result.trajectory()).singleElement()
                .satisfies(step -> assertThat(step.status()).isEqualTo("COMPLETED"));
    }

    @Test
    void feedsToolObservationBackToModelBeforeCompleting() {
        ChatModel model = mock(ChatModel.class);
        ChatResponse toolRequest = response("", List.of(new AssistantMessage.ToolCall(
                "call-1", "function", "search_available_rooms", "{\"maxMonthlyRent\":2500}")));
        ChatResponse finalAnswer = response("找到一套真实房源", List.of());
        when(model.call(any(Prompt.class))).thenReturn(toolRequest, finalAnswer);
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.callbacks()).thenReturn(List.of());
        ToolCallingManager manager = mock(ToolCallingManager.class);
        ToolExecutionResult execution = mock(ToolExecutionResult.class);
        when(execution.conversationHistory()).thenReturn(List.<Message>of(new UserMessage("tool result")));
        when(manager.executeToolCalls(any(Prompt.class), any(ChatResponse.class))).thenReturn(execution);
        DefaultRentalAgentRuntime runtime = runtime(model, registry, manager);

        AgentResult result = runtime.execute(context(3));

        assertThat(result.answer()).isEqualTo("找到一套真实房源");
        verify(registry).validate(any(), any(), any());
        verify(manager).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
        assertThat(result.trajectory()).extracting(AgentStep::status)
                .containsExactly("TOOL_REQUEST", "COMPLETED");
    }

    @Test
    void rejectsWhenModelKeepsRequestingToolsPastStepBudget() {
        ChatModel model = mock(ChatModel.class);
        ChatResponse toolRequest = response("", List.of(new AssistantMessage.ToolCall(
                "call-1", "function", "search_available_rooms", "{}")));
        when(model.call(any(Prompt.class))).thenReturn(toolRequest);
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.callbacks()).thenReturn(List.of());
        ToolCallingManager manager = mock(ToolCallingManager.class);
        ToolExecutionResult execution = mock(ToolExecutionResult.class);
        when(execution.conversationHistory()).thenReturn(List.<Message>of(new UserMessage("tool result")));
        when(manager.executeToolCalls(any(Prompt.class), any(ChatResponse.class))).thenReturn(execution);
        DefaultRentalAgentRuntime runtime = runtime(model, registry, manager);

        assertThatThrownBy(() -> runtime.execute(context(2)))
                .isInstanceOfSatisfying(AgentExecutionException.class,
                        error -> assertThat(error.getErrorType()).isEqualTo("STEP_LIMIT"));
        verify(manager, org.mockito.Mockito.times(2))
                .executeToolCalls(any(Prompt.class), any(ChatResponse.class));
    }

    @SuppressWarnings("unchecked")
    private DefaultRentalAgentRuntime runtime(ChatModel model, ToolRegistry registry, ToolCallingManager manager) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return new DefaultRentalAgentRuntime(provider, registry, new AiAgentProperties(), executor, manager);
    }

    private AgentContext context(int maxSteps) {
        return new AgentContext(7L, "conv-1", "预算2500", List.of(), AgentGoal.FIND_ROOM, maxSteps);
    }

    private ChatResponse response(String text, List<AssistantMessage.ToolCall> calls) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text, Map.of(), calls))));
    }
}
