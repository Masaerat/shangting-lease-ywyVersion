package com.atguigu.lease.web.app.tools;

import com.atguigu.lease.notification.AppointmentNotificationStore;
import com.atguigu.lease.notification.AppointmentDeliveryStatus;
import com.atguigu.lease.web.app.service.ai.agent.AgentExecutionState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentStatusToolTest {
    private final AppointmentNotificationStore store = mock(AppointmentNotificationStore.class);
    private final AppointmentStatusTool tool = new AppointmentStatusTool(store);
    private final AgentExecutionState state = new AgentExecutionState();

    private ToolContext context() {
        return new ToolContext(Map.of(AgentExecutionState.CONTEXT_KEY, state, AgentExecutionState.USER_ID_KEY, 7L));
    }

    @Test
    void usesAuthenticatedUserAndRecordsRealDeliveryObservation() {
        var status = new AppointmentDeliveryStatus(20L, 100L, 1, null, "DELIVERED", 30L);
        when(store.status(7L, 20L)).thenReturn(status);
        assertThat(tool.status(20L, context())).isSameAs(status);
        assertThat(state.observations()).singleElement().satisfies(o -> {
            assertThat(o.tool()).isEqualTo("get_appointment_status");
            assertThat(o.payload()).isSameAs(status);
        });
        verify(store).status(7L, 20L);
    }

    @Test
    void cannotReadWithoutAuthenticatedContext() {
        assertThatThrownBy(() -> tool.status(20L, new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(store);
    }

    @Test
    void listsOnlyAuthenticatedUsersNotifications() {
        when(store.list(7L, 20)).thenReturn(List.of());
        assertThat(tool.list(null, context())).isEmpty();
        verify(store).list(7L, 20);
        assertThat(state.observations()).singleElement().satisfies(o -> assertThat(o.tool()).isEqualTo("list_my_notifications"));
    }
}
