package com.atguigu.lease.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class OutboxPublishScheduler {

    private final AppointmentOutboxPublisher publisher;

    public OutboxPublishScheduler(AppointmentOutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.fixed-delay-ms:1000}",
            initialDelayString = "${app.outbox.initial-delay-ms:2000}")
    public void publishPendingAppointments() {
        publisher.publishBatch();
    }
}
