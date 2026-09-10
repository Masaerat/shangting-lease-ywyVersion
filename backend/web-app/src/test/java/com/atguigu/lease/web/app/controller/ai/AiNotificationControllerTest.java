package com.atguigu.lease.web.app.controller.ai;

import com.atguigu.lease.common.login.LoginUser;
import com.atguigu.lease.common.login.LoginUserHolder;
import com.atguigu.lease.common.exception.LeaseException;
import com.atguigu.lease.notification.AppointmentNotificationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiNotificationControllerTest {
    private final AppointmentNotificationStore store = mock(AppointmentNotificationStore.class);
    private final AiNotificationController controller = new AiNotificationController(store);

    @AfterEach
    void clear() { LoginUserHolder.clear(); }

    @Test
    void requiresLogin() {
        assertThatThrownBy(() -> controller.list(20)).isInstanceOf(LeaseException.class);
        verifyNoInteractions(store);
    }

    @Test
    void scopesQueriesAndReadUpdatesToLoginUser() {
        LoginUserHolder.setLoginUser(new LoginUser(7L, "test"));
        controller.list(20);
        when(store.markRead(7L, 10L)).thenReturn(true);
        assertThat(controller.markRead(10L).getCode()).isEqualTo(200);
        verify(store).list(7L, 20);
        verify(store).markRead(7L, 10L);
    }

    @Test
    void missingAndUnauthorizedRecordsHaveSameNotFoundResponse() {
        LoginUserHolder.setLoginUser(new LoginUser(7L, "test"));
        assertThatThrownBy(() -> controller.status(20L)).isInstanceOfSatisfying(LeaseException.class,
                error -> assertThat(error.getCode()).isEqualTo(404));
        assertThatThrownBy(() -> controller.markRead(10L)).isInstanceOfSatisfying(LeaseException.class,
                error -> assertThat(error.getCode()).isEqualTo(404));
    }
}
