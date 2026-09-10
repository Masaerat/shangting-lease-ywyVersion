package com.atguigu.lease.web.app.service.ai.agent;

import com.atguigu.lease.config.ai.AiAgentProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentContractTest {

    @Test
    void propertiesHaveBoundedDemoDefaults() {
        AiAgentProperties properties = new AiAgentProperties();

        assertThat(properties.getMaxSteps()).isEqualTo(6);
        assertThat(properties.getMaxSameToolCalls()).isEqualTo(3);
        assertThat(properties.getTimeoutMs()).isEqualTo(15_000);
        assertThat(properties.getToolTimeoutMs()).isEqualTo(3_000);
        assertThat(properties.isTrajectoryEnabled()).isTrue();
    }

    @Test
    void contextCopiesHistoryAndRequiresAnAuthenticatedUser() {
        List<String> history = new java.util.ArrayList<>(List.of("用户:预算2500"));
        AgentContext context = new AgentContext(
                7L, "conv-1", "押金怎么退", history, AgentGoal.ANSWER_POLICY, 6);
        history.add("later mutation");

        assertThat(context.history()).containsExactly("用户:预算2500");
        assertThatThrownBy(() -> new AgentContext(
                null, "conv-1", "押金怎么退", List.of(), AgentGoal.ANSWER_POLICY, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user id");
    }
}
