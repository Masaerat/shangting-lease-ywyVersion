package com.atguigu.lease.consumer.appointment;

import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AppointmentMessageConsumerTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private AppointmentEventDeduplicator deduplicator;

    private AppointmentMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AppointmentMessageConsumer(rabbitTemplate, deduplicator);
    }

    @Test
    void duplicateOutboxEventIsSkipped() {
        AppointmentMessage message = message(88L);
        when(deduplicator.tryClaim(88L)).thenReturn(false);

        consumer.handleAppointmentCreate(message);

        verify(rabbitTemplate, never()).convertAndSend(
                eq(RabbitMQConfig.APPOINTMENT_EXCHANGE),
                eq(RabbitMQConfig.NOTIFY_ROUTING_KEY), any(Object.class));
        verify(deduplicator, never()).markProcessed(88L);
    }

    @Test
    void firstDeliveryMarksEventProcessedAfterNotification() {
        AppointmentMessage message = message(89L);
        when(deduplicator.tryClaim(89L)).thenReturn(true);

        consumer.handleAppointmentCreate(message);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.APPOINTMENT_EXCHANGE),
                eq(RabbitMQConfig.NOTIFY_ROUTING_KEY), any(Object.class));
        verify(deduplicator).markProcessed(89L);
    }

    @Test
    void exhaustedNotificationRetriesReleaseClaimAndRejectMessage() {
        AppointmentMessage message = message(90L);
        when(deduplicator.tryClaim(90L)).thenReturn(true);
        doThrow(new IllegalStateException("notify broker unavailable"))
                .when(rabbitTemplate).convertAndSend(
                        eq(RabbitMQConfig.APPOINTMENT_EXCHANGE),
                        eq(RabbitMQConfig.NOTIFY_ROUTING_KEY), any(Object.class));

        assertThatThrownBy(() -> consumer.handleAppointmentCreate(message))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        verify(deduplicator).release(90L);
        verify(deduplicator, never()).markProcessed(90L);
    }

    private AppointmentMessage message(Long eventId) {
        return AppointmentMessage.builder()
                .eventId(eventId)
                .appointmentId(101L)
                .roomId(930001L)
                .userId(990001L)
                .name("演示用户")
                .phone("13800000000")
                .apartmentId(920001L)
                .appointmentTime(new Date())
                .appointmentStatus("WAITING")
                .messageType("CREATE")
                .build();
    }
}
