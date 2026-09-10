package com.atguigu.lease.web.app.service.ai.appointment;

import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentConfirmResponse;
import com.atguigu.lease.web.app.vo.ai.appointment.AppointmentDraftRequest;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=docker",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "spring.ai.openai.api-key=",
        "app.datasource.pg.url=",
        "app.outbox.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class AppointmentConfirmationServiceIT {

    private static final Long USER_ID = 990001L;
    private static final Long ROOM_ID = 930001L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("lease")
            .withUsername("lease")
            .withPassword("lease");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private AppointmentDraftService draftService;
    @Autowired private AppointmentConfirmationService confirmationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentConfirmationCreatesOneAppointmentAndOneOutboxEvent() throws Exception {
        AppointmentDraftRequest request = new AppointmentDraftRequest();
        request.setRoomId(ROOM_ID);
        request.setName("并发测试用户");
        request.setPhone("13800000000");
        request.setAppointmentTime(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0));
        String token = draftService.create(USER_ID, request).confirmationToken();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AppointmentConfirmResponse>> futures = List.of(
                    executor.submit(() -> confirmTogether(token, ready, start)),
                    executor.submit(() -> confirmTogether(token, ready, start)));
            ready.await();
            start.countDown();
            List<AppointmentConfirmResponse> results = List.of(
                    futures.get(0).get(), futures.get(1).get());

            assertThat(results).extracting(AppointmentConfirmResponse::appointmentId)
                    .containsOnly(results.getFirst().appointmentId());
            assertThat(results).extracting(AppointmentConfirmResponse::idempotentReplay)
                    .containsExactlyInAnyOrder(false, true);
            Long appointmentId = results.getFirst().appointmentId();
            assertThat(count("view_appointment", "id = ?", appointmentId)).isEqualTo(1);
            assertThat(count("ai_appointment_idempotency", "appointment_id = ?", appointmentId)).isEqualTo(1);
            assertThat(count("appointment_event_outbox", "aggregate_id = ?", appointmentId)).isEqualTo(1);
        }
    }

    private AppointmentConfirmResponse confirmTogether(String token, CountDownLatch ready,
                                                        CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return confirmationService.confirm(USER_ID, token);
    }

    private long count(String table, String condition, Object value) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + condition, Long.class, value);
        return count == null ? 0 : count;
    }
}
