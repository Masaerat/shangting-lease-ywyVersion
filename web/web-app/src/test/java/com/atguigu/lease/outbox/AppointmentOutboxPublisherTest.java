package com.atguigu.lease.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentOutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-08-05T03:00:00Z");

    @Mock private AppointmentOutboxRepository repository;
    @Mock private AppointmentEventSender sender;

    private AppointmentOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AppointmentOutboxPublisher(
                repository, sender, Clock.fixed(NOW, ZoneOffset.UTC), 20);
    }

    @Test
    void confirmedMessageMarksEventPublished() {
        OutboxEvent event = event(11L, 1);
        when(repository.claimPending(20, NOW)).thenReturn(List.of(event));

        publisher.publishBatch();

        verify(sender).send(event);
        verify(repository).markPublished(11L, NOW);
        verify(repository, never()).markFailed(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void brokerFailureSchedulesExponentialRetry() {
        OutboxEvent event = event(12L, 2);
        when(repository.claimPending(20, NOW)).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new OutboxPublishException("broker unavailable"))
                .when(sender).send(event);

        publisher.publishBatch();

        verify(repository).markFailed(12L, 2, NOW.plusSeconds(4), "broker unavailable", false);
        verify(repository, never()).markPublished(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fifthFailureMarksEventDead() {
        OutboxEvent event = event(13L, 5);
        when(repository.claimPending(20, NOW)).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new OutboxPublishException("nack"))
                .when(sender).send(event);

        publisher.publishBatch();

        verify(repository).markFailed(13L, 5, NOW, "nack", true);
    }

    private OutboxEvent event(Long id, int attempts) {
        return new OutboxEvent(id, 101L, "APPOINTMENT_CREATED", "{\"appointmentId\":101}", attempts);
    }
}
