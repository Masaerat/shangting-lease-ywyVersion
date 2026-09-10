package com.atguigu.lease.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Repository
public class AppointmentOutboxRepository {

    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public AppointmentOutboxRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public List<OutboxEvent> claimPending(int batchSize, Instant now) {
        if (batchSize < 1) return List.of();
        List<OutboxEvent> claimed = transactionTemplate.execute(status -> {
            List<OutboxEvent> events = jdbcTemplate.query("""
                            SELECT id, aggregate_id, event_type, payload_json, attempts
                            FROM appointment_event_outbox
                            WHERE status = 'PENDING' AND next_attempt_at <= ?
                            ORDER BY id
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED
                            """,
                    (rs, rowNum) -> new OutboxEvent(
                            rs.getLong("id"),
                            rs.getLong("aggregate_id"),
                            rs.getString("event_type"),
                            rs.getString("payload_json"),
                            rs.getInt("attempts") + 1),
                    Timestamp.from(now), 1); // Do not lease a batch that waits behind slow broker confirms.
            Instant leaseUntil = now.plus(CLAIM_LEASE);
            for (OutboxEvent event : events) {
                jdbcTemplate.update("""
                                UPDATE appointment_event_outbox
                                SET attempts = ?, next_attempt_at = ?
                                WHERE id = ? AND status = 'PENDING'
                                """,
                        event.attempts(), Timestamp.from(leaseUntil), event.id());
            }
            return events;
        });
        return claimed == null ? List.of() : claimed;
    }

    public void markPublished(Long eventId, int claimedAttempt, Instant publishedAt) {
        jdbcTemplate.update("""
                        UPDATE appointment_event_outbox
                        SET status = 'PUBLISHED', published_at = ?, last_error = NULL
                        WHERE id = ? AND status = 'PENDING' AND attempts = ?
                        """,
                Timestamp.from(publishedAt), eventId, claimedAttempt);
    }

    public void markFailed(Long eventId, int attempts, Instant nextAttemptAt,
                           String error, boolean dead) {
        jdbcTemplate.update("""
                        UPDATE appointment_event_outbox
                        SET status = ?, attempts = ?, next_attempt_at = ?, last_error = ?
                        WHERE id = ? AND status = 'PENDING' AND attempts = ?
                        """,
                dead ? "DEAD" : "PENDING",
                attempts,
                Timestamp.from(nextAttemptAt),
                truncate(error, 500),
                eventId, attempts);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
