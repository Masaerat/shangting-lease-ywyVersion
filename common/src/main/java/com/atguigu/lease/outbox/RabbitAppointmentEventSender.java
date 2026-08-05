package com.atguigu.lease.outbox;

import com.atguigu.lease.common.utils.JsonUtil;
import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitAppointmentEventSender implements AppointmentEventSender {

    private static final long CONFIRM_TIMEOUT_SECONDS = 10;

    private final RabbitTemplate rabbitTemplate;

    public RabbitAppointmentEventSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(OutboxEvent event) {
        AppointmentMessage message = JsonUtil.parseObject(event.payloadJson(), AppointmentMessage.class);
        message.setEventId(event.id());
        CorrelationData correlationData = new CorrelationData(String.valueOf(event.id()));
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.APPOINTMENT_EXCHANGE,
                RabbitMQConfig.CREATE_ROUTING_KEY,
                message,
                correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new OutboxPublishException("RabbitMQ nack: " + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new OutboxPublishException("RabbitMQ returned unroutable appointment event");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("RabbitMQ publisher confirm interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboxPublishException("RabbitMQ publisher confirm failed", exception);
        }
    }
}
