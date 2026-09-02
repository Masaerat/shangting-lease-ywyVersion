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
    void registersOnlyTheFiveApprovedTools() {
        ToolRegistry registry = registry(new AiAgentProperties());

        assertThat(registry.names()).containsExactly(
                "calculate_move_in_cost",
                "create_appointment_draft",
                "get_room_detail",
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
        return new ToolRegistry(
                new AgentRoomSearchTool(mock(RoomSearchTool.class)),
                new RoomDetailTool(
                        mock(RoomInfoService.class), mock(ApartmentInfoService.class), mock(PaymentTypeService.class)),
                new MoveInCostTool(mock(RoomInfoService.class), mock(PaymentTypeService.class)),
                new RentalKnowledgeTool(mock(RentalKnowledgeService.class)),
                new AppointmentDraftTool(mock(AppointmentDraftService.class)),
                new PermissionPolicy(), properties, executor);
    }

    private AssistantMessage.ToolCall call(String name) {
        return new AssistantMessage.ToolCall("call-1", "function", name, "{}");
    }
}
