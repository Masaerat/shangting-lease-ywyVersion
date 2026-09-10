package com.atguigu.lease.web.app.service.ai.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTaskExecutorTest {

    private final AgentTaskExecutor executor = new AgentTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void interruptsOperationAfterConfiguredTimeout() {
        assertThatThrownBy(() -> executor.call(() -> {
            Thread.sleep(500);
            return "late";
        }, Duration.ofMillis(20), "TOOL_TIMEOUT"))
                .isInstanceOfSatisfying(AgentExecutionException.class,
                        error -> assertThat(error.getErrorType()).isEqualTo("TOOL_TIMEOUT"));
    }
}
