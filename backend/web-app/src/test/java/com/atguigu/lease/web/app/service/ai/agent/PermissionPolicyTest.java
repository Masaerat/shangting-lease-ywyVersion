package com.atguigu.lease.web.app.service.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionPolicyTest {

    @Test
    void agentCanReadAndPrepareButCannotExecuteWriteTools() {
        PermissionPolicy policy = new PermissionPolicy();
        AgentContext context = new AgentContext(
                7L, "conv-1", "预约看房", List.of(), AgentGoal.PREPARE_APPOINTMENT, 6);

        policy.requireAllowed("search_available_rooms", AgentPermission.READ, context);
        policy.requireAllowed("create_appointment_draft", AgentPermission.PREPARE, context);
        assertThatThrownBy(() -> policy.requireAllowed("confirm_appointment", AgentPermission.WRITE, context))
                .isInstanceOf(AgentExecutionException.class)
                .hasMessageContaining("not allowed");
    }
}
