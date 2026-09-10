package com.atguigu.lease.consumer.appointment;

import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.atguigu.lease.notification.AppointmentNotificationStore;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentMessageConsumerTest {
    private final AppointmentNotificationStore store = mock(AppointmentNotificationStore.class);
    private final AppointmentMessageConsumer consumer = new AppointmentMessageConsumer(store);
    private final AppointmentMessage message = AppointmentMessage.builder().eventId(10L).appointmentId(20L).userId(7L).build();

    @Test
    void duplicateDeliveriesAlwaysReachTheDatabaseIdempotencyBoundary() {
        consumer.handleAppointmentCreate(message);
        consumer.handleAppointmentCreate(message);
        verify(store, times(2)).persistCreated(message);
    }

    @Test
    void storageFailureIsRejectedInsteadOfAcknowledged() {
        doThrow(new IllegalStateException("database unavailable")).when(store).persistCreated(message);
        assertThatThrownBy(() -> consumer.handleAppointmentCreate(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(store, times(3)).persistCreated(message);
    }

    @Test
    void transientFailureCanRecoverBeforeAck() {
        doThrow(new IllegalStateException("transient")).doNothing().when(store).persistCreated(message);
        consumer.handleAppointmentCreate(message);
        verify(store, times(2)).persistCreated(message);
    }

    @Test
    void invalidOwnerIsRejectedWithoutRetry() {
        doThrow(new IllegalArgumentException("owner")).when(store).persistCreated(message);
        assertThatThrownBy(() -> consumer.handleAppointmentCreate(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(store).persistCreated(message);
    }
}
