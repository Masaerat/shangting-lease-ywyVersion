package com.atguigu.lease.outbox;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AppointmentOutboxRepositoryTest {
    private JdbcTemplate jdbc;
    private AppointmentOutboxRepository repository;
    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        repository = new AppointmentOutboxRepository(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)));
        jdbc.execute("CREATE TABLE appointment_event_outbox(id bigint primary key, aggregate_id bigint, event_type varchar(64), payload_json varchar(1000), status varchar(16), attempts int, next_attempt_at timestamp, published_at timestamp, last_error varchar(500))");
        jdbc.update("INSERT INTO appointment_event_outbox VALUES(1,20,'APPOINTMENT_CREATED','{}','PENDING',2,CURRENT_TIMESTAMP,NULL,NULL)");
    }

    @Test
    void stalePublisherCannotOverwriteNewClaim() {
        repository.markPublished(1L, 1, NOW);
        assertThat(status()).isEqualTo("PENDING");
        repository.markFailed(1L, 1, NOW, "stale", true);
        assertThat(status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM appointment_event_outbox", Integer.class)).isEqualTo(2);
        repository.markPublished(1L, 2, NOW);
        assertThat(status()).isEqualTo("PUBLISHED");
    }

    @Test
    void failedClaimPreservesAttemptAndTruncatesError() {
        repository.markFailed(1L, 2, NOW, "x".repeat(600), true);
        assertThat(status()).isEqualTo("DEAD");
        assertThat(jdbc.queryForObject("SELECT last_error FROM appointment_event_outbox", String.class)).hasSize(500);
    }

    @Test
    void claimLeasesOnlyOneEventAndRecoversAfterExpiry() {
        jdbc.update("UPDATE appointment_event_outbox SET next_attempt_at = ?", java.sql.Timestamp.from(NOW));
        jdbc.update("INSERT INTO appointment_event_outbox SELECT 2,aggregate_id,event_type,payload_json,status,attempts,next_attempt_at,published_at,last_error FROM appointment_event_outbox WHERE id=1");
        assertThat(repository.claimPending(20, NOW)).singleElement().satisfies(e -> assertThat(e.attempts()).isEqualTo(3));
        assertThat(repository.claimPending(20, NOW)).singleElement().satisfies(e -> assertThat(e.id()).isEqualTo(2));
        assertThat(repository.claimPending(20, NOW)).isEmpty();
        assertThat(repository.claimPending(20, NOW.plusSeconds(31))).singleElement().satisfies(e -> assertThat(e.attempts()).isEqualTo(4));
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM appointment_event_outbox WHERE id=1", String.class);
    }
}
