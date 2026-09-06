package com.atguigu.lease.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class AppointmentOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppointmentOutboxPublisher.class);
    private static final int MAX_ATTEMPTS = 5;

    private final AppointmentOutboxRepository repository;
    private final AppointmentEventSender sender;
    private final Clock clock;
    private final int batchSize;

    @Autowired
    public AppointmentOutboxPublisher(AppointmentOutboxRepository repository,
                                      AppointmentEventSender sender) {
        this(repository, sender, Clock.systemUTC(), 1);
    }

    AppointmentOutboxPublisher(AppointmentOutboxRepository repository,
                               AppointmentEventSender sender, Clock clock, int batchSize) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    public int publishBatch() {
        Instant now = clock.instant();
        int published = 0;
        for (OutboxEvent event : repository.claimPending(batchSize, now)) {
            try {
                sender.send(event);
                repository.markPublished(event.id(), event.attempts(), clock.instant());
                published++;
            } catch (RuntimeException exception) {
                boolean dead = event.attempts() >= MAX_ATTEMPTS;
                Instant nextAttempt = dead ? clock.instant() : clock.instant().plusSeconds(backoffSeconds(event.attempts()));
                repository.markFailed(event.id(), event.attempts(), nextAttempt,
                        errorMessage(exception), dead);
                LOGGER.warn("Appointment outbox event {} publish failed on attempt {}",
                        event.id(), event.attempts(), exception);
            }
        }
        return published;
    }

    private static long backoffSeconds(int attempts) {
        return 1L << Math.min(Math.max(attempts, 1), 8);
    }

    private static String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
