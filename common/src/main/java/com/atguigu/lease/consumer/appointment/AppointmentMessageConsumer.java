package com.atguigu.lease.consumer.appointment;

import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentMessage;
import com.atguigu.lease.notification.AppointmentNotificationStore;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMessageConsumer {
    private final AppointmentNotificationStore notifications;

    public AppointmentMessageConsumer(AppointmentNotificationStore notifications) {
        this.notifications = notifications;
    }

    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_CREATE_QUEUE)
    public void handleAppointmentCreate(AppointmentMessage message) {
        persistWithRetry(() -> notifications.persistCreated(message));
    }

    static void persistWithRetry(Runnable action) {
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                action.run(); // Commit before container ACK. Duplicate deliveries are database-idempotent.
                return;
            } catch (IllegalArgumentException invalid) {
                throw new AmqpRejectAndDontRequeueException("Invalid appointment event", invalid);
            } catch (RuntimeException error) {
                failure = error;
            }
        }
        throw new AmqpRejectAndDontRequeueException("Notification persistence failed; inspect DLQ", failure);
    }
    // No DLQ listener: preserve failures for inspection and replay.
}
