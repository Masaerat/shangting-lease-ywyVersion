package com.atguigu.lease.consumer.appointment;

import com.atguigu.lease.config.RabbitMQConfig;
import com.atguigu.lease.message.appointment.AppointmentNotificationMessage;
import com.atguigu.lease.notification.AppointmentNotificationStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Compatibility for status-change producers. The new CREATE flow is single-hop. */
@Component
public class AppointmentNotificationConsumer {
    private final AppointmentNotificationStore notifications;

    public AppointmentNotificationConsumer(AppointmentNotificationStore notifications) {
        this.notifications = notifications;
    }

    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_NOTIFY_QUEUE)
    public void handleNotification(AppointmentNotificationMessage message) {
        AppointmentMessageConsumer.persistWithRetry(() -> notifications.persistLegacy(message));
    }
}
