package com.atguigu.lease.web.app.service.ai.agent;

import com.atguigu.lease.config.ai.AiAgentProperties;
import com.atguigu.lease.web.app.service.ApartmentInfoService;
import com.atguigu.lease.web.app.service.PaymentTypeService;
import com.atguigu.lease.web.app.service.RoomInfoService;
import com.atguigu.lease.web.app.service.ai.appointment.AppointmentDraftService;
import com.atguigu.lease.web.app.service.ai.rag.RentalKnowledgeService;
import com.atguigu.lease.web.app.tools.AgentRoomSearchTool;
import com.atguigu.lease.web.app.tools.AppointmentDraftTool;
import com.atguigu.lease.web.app.tools.MoveInCostTool;
import com.atguigu.lease.web.app.tools.RentalKnowledgeTool;
import com.atguigu.lease.web.app.tools.RoomDetailTool;
import com.atguigu.lease.web.app.tools.RoomSearchTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ToolRegistryTest {

    private final AgentTaskExecutor executor = new AgentTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void registersOnlyTheSevenApprovedTools() {
        ToolRegistry registry = registry(new AiAgentProperties());

        assertThat(registry.names()).containsExactly(
                "calculate_move_in_cost",
                "create_appointment_draft",
                "get_appointment_status",
                "get_room_detail",
                "list_my_notifications",
                "search_available_rooms",
                "search_rental_knowledge");
        assertThat(registry.names()).doesNotContain("confirm_appointment");
    }

    @Test
    void rejectsUnknownToolsAndRepeatedCalls() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setMaxSameToolCalls(1);
        ToolRegistry registry = registry(properties);
        AgentContext context = new AgentContext(
                7L, "conv-1", "找房", List.of(), AgentGoal.FIND_ROOM, 4);
        AgentExecutionState state = new AgentExecutionState();

        assertThatThrownBy(() -> registry.validate(
                List.of(call("drop_database")), context, state))
                .isInstanceOfSatisfying(AgentExecutionException.class,
                        error -> assertThat(error.getErrorType()).isEqualTo("UNKNOWN_TOOL"));

        registry.validate(List.of(call("search_available_rooms")), context, state);
        assertThatThrownBy(() -> registry.validate(
                List.of(call("search_available_rooms")), context, state))
                .isInstanceOfSatisfying(AgentExecutionException.class,
                        error -> assertThat(error.getErrorType()).isEqualTo("TOOL_CALL_LIMIT"));
    }

    private ToolRegistry registry(AiAgentProperties properties) {
        return registry(properties, mock(com.atguigu.lease.notification.AppointmentNotificationStore.class));
    }

    private ToolRegistry registry(AiAgentProperties properties, com.atguigu.lease.notification.AppointmentNotificationStore notifications) {
        return new ToolRegistry(
                new AgentRoomSearchTool(mock(RoomSearchTool.class)),
                new RoomDetailTool(
                        mock(RoomInfoService.class), mock(ApartmentInfoService.class), mock(PaymentTypeService.class)),
                new MoveInCostTool(mock(RoomInfoService.class), mock(PaymentTypeService.class)),
                new RentalKnowledgeTool(mock(RentalKnowledgeService.class)),
                new AppointmentDraftTool(mock(AppointmentDraftService.class)),
                new com.atguigu.lease.web.app.tools.AppointmentStatusTool(notifications),
                new PermissionPolicy(), properties, executor);
    }

    @Test
    @SuppressWarnings("unchecked")
    void realToolCallingManagerInvokesUserScopedDeliveryTool() {
        var store = mock(com.atguigu.lease.notification.AppointmentNotificationStore.class);
        var status = new com.atguigu.lease.notification.AppointmentDeliveryStatus(20L, 100L, 1, null, "DELIVERED", 30L);
        org.mockito.Mockito.when(store.status(7L, 20L)).thenReturn(status);
        var model = mock(org.springframework.ai.chat.model.ChatModel.class);
        var toolRequest = new org.springframework.ai.chat.model.ChatResponse(List.of(
                new org.springframework.ai.chat.model.Generation(new AssistantMessage("", java.util.Map.of(), List.of(
                        new AssistantMessage.ToolCall("call-status", "function", "get_appointment_status", "{\"appointmentId\":20}"))))));
        var answer = new org.springframework.ai.chat.model.ChatResponse(List.of(
                new org.springframework.ai.chat.model.Generation(new AssistantMessage("站内通知已落库"))));
        org.mockito.Mockito.when(model.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(toolRequest, answer);
        org.springframework.beans.factory.ObjectProvider<org.springframework.ai.chat.model.ChatModel> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(model);
        var properties = new AiAgentProperties();
        var runtime = new DefaultRentalAgentRuntime(provider, registry(properties, store), properties, executor);
        var result = runtime.execute(new AgentContext(7L, "conv", "预约20的状态", List.of(), AgentGoal.ANSWER, 4));
        assertThat(result.answer()).isEqualTo("站内通知已落库");
        assertThat(result.observations()).singleElement().satisfies(o -> assertThat(o.payload()).isEqualTo(status));
        org.mockito.Mockito.verify(store).status(7L, 20L);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.times(2)).call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void callbacksCannotBypassAuthenticatedContext() {
        var callback = registry(new AiAgentProperties()).callbacks().getFirst();
        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOfSatisfying(AgentExecutionException.class,
                        error -> assertThat(error.getErrorType()).isEqualTo("INVALID_CONTEXT"));
    }

    private AssistantMessage.ToolCall call(String name) {
        return new AssistantMessage.ToolCall("call-1", "function", name, "{}");
    }
}
