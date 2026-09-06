package com.atguigu.lease.outbox;

import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitAppointmentEventSenderTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final RabbitAppointmentEventSender sender = new RabbitAppointmentEventSender(rabbit);
    private final OutboxEvent event = new OutboxEvent(10L, 20L, "APPOINTMENT_CREATED",
            "{\"appointmentId\":20,\"userId\":7,\"messageType\":\"CREATE\"}", 1);

    private void confirms(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            AppointmentMessage message = invocation.getArgument(2);
            assertThat(message.getEventId()).isEqualTo(10L);
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) correlation.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                    312, "NO_ROUTE", RabbitMQConfig.APPOINTMENT_EXCHANGE, RabbitMQConfig.CREATE_ROUTING_KEY));
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbit).convertAndSend(eq(RabbitMQConfig.APPOINTMENT_EXCHANGE),
                eq(RabbitMQConfig.CREATE_ROUTING_KEY), any(Object.class), any(CorrelationData.class));
    }

    @Test
    void brokerAckCompletesPublication() {
        confirms(true, false);
        assertThatCode(() -> sender.send(event)).doesNotThrowAnyException();
    }

    @Test
    void ackWithReturnIsNotSuccessfulRouting() {
        confirms(true, true);
        assertThatThrownBy(() -> sender.send(event)).isInstanceOf(OutboxPublishException.class).hasMessageContaining("unroutable");
    }

    @Test
    void nackFailsPublication() {
        confirms(false, false);
        assertThatThrownBy(() -> sender.send(event)).isInstanceOf(OutboxPublishException.class).hasMessageContaining("nack");
    }

    @Test
    void sharedJsonConverterRoundTripsAppointmentPayload() {
        var converter = new RabbitMQConfig().appointmentMessageConverter();
        var input = AppointmentMessage.builder().eventId(10L).appointmentId(20L).userId(7L).build();
        var message = converter.toMessage(input, new MessageProperties());
        assertThat(converter.fromMessage(message)).isEqualTo(input);
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
    }
}
