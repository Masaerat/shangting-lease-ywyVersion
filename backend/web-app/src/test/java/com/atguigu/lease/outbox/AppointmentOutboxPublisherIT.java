package com.atguigu.lease.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=docker",
        "spring.ai.openai.api-key=",
        "app.datasource.pg.url=",
        "app.outbox.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class AppointmentOutboxPublisherIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("lease")
            .withUsername("lease")
            .withPassword("lease");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    private static final GenericContainer<?> RABBIT = new GenericContainer<>(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "lease")
            .withEnv("RABBITMQ_DEFAULT_PASS", "lease")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "lease");
        registry.add("spring.rabbitmq.password", () -> "lease");
    }

    @Autowired private AppointmentOutboxPublisher publisher;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void pendingEventPublishesAfterBrokerRecovery() {
        rabbitTemplate.execute(channel -> channel.isOpen());
        long eventId = insertPendingEvent();

        RABBIT.getDockerClient().pauseContainerCmd(RABBIT.getContainerId()).exec();
        try {
            assertThat(publisher.publishBatch()).isZero();
            assertThat(status(eventId)).isEqualTo("PENDING");
        } finally {
            RABBIT.getDockerClient().unpauseContainerCmd(RABBIT.getContainerId()).exec();
        }

        jdbcTemplate.update("UPDATE appointment_event_outbox SET next_attempt_at = NOW() WHERE id = ?", eventId);
        assertThat(publisher.publishBatch()).isEqualTo(1);
        assertThat(status(eventId)).isEqualTo("PUBLISHED");
    }

    private long insertPendingEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appointmentId", 101L);
        payload.put("userId", 990001L);
        payload.put("roomId", 930001L);
        payload.put("apartmentId", 920001L);
        payload.put("name", "恢复测试用户");
        payload.put("phone", "13800000000");
        payload.put("appointmentTime", new Date(System.currentTimeMillis() + 86_400_000L));
        payload.put("appointmentStatus", "WAITING");
        payload.put("messageType", "CREATE");
        jdbcTemplate.update("""
                        INSERT INTO appointment_event_outbox
                          (aggregate_id, event_type, payload_json, status, attempts, next_attempt_at, created_at)
                        VALUES (?, 'APPOINTMENT_CREATED', ?, 'PENDING', 0, ?, ?)
                        """,
                101L,
                com.atguigu.lease.common.utils.JsonUtil.toJsonString(payload),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String status(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM appointment_event_outbox WHERE id = ?", String.class, eventId);
    }
}
