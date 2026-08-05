package com.atguigu.lease.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=docker",
        "server.port=0",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "spring.ai.openai.api-key=",
        "app.datasource.pg.url=",
        "app.outbox.enabled=false",
        "app.demo-login.enabled=true"
})
@Testcontainers(disabledWithoutDocker = true)
class RentalAgentClosedLoopIT {

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

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void fallbackChatToConfirmedAppointmentIsClosedLoop() throws Exception {
        String token = login("13800000000", "888888");

        List<JsonNode> chatEvents = chat(token, "预算2500元，并说明押金怎么退");
        JsonNode meta = event(chatEvents, "meta").path("payload");
        JsonNode recommendations = event(chatEvents, "recommendations").path("payload");
        JsonNode citations = event(chatEvents, "citations").path("payload");
        assertThat(meta.path("mode").asText()).isEqualTo("FALLBACK");
        assertThat(recommendations.isArray()).isTrue();
        assertThat(recommendations).isNotEmpty();
        assertThat(citations.isArray()).isTrue();
        assertThat(citations).isNotEmpty();

        assertThat(appointments(token)).isEmpty();
        long roomId = recommendations.get(0).path("roomId").asLong();
        JsonNode draft = postJson("/app/ai/appointments/draft", token, Map.of(
                "roomId", roomId,
                "name", "闭环测试用户",
                "phone", "13800000000",
                "appointmentTime", LocalDateTime.now()
                        .plusDays(1)
                        .truncatedTo(ChronoUnit.MINUTES)
                        .toString(),
                "additionalInfo", "AI 找房闭环验收"
        )).path("data");

        assertThat(appointments(token)).isEmpty();
        String confirmationToken = draft.path("confirmationToken").asText();
        assertThat(confirmationToken).isNotBlank();

        JsonNode first = confirm(token, confirmationToken);
        JsonNode replay = confirm(token, confirmationToken);
        long appointmentId = first.path("appointmentId").asLong();
        assertThat(appointmentId).isPositive();
        assertThat(first.path("idempotentReplay").asBoolean()).isFalse();
        assertThat(replay.path("appointmentId").asLong()).isEqualTo(appointmentId);
        assertThat(replay.path("idempotentReplay").asBoolean()).isTrue();
        assertThat(appointments(token).findValuesAsText("id")).contains(Long.toString(appointmentId));
    }

    private String login(String phone, String code) throws Exception {
        JsonNode response = postJson("/app/login", null, Map.of("phone", phone, "code", code));
        assertSuccess(response);
        return response.path("data").asText();
    }

    private List<JsonNode> chat(String token, String message) throws Exception {
        HttpRequest request = request("/app/ai/chat", token)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of(
                        "conversationId", "closed-loop-it",
                        "message", message))))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        List<JsonNode> events = new ArrayList<>();
        for (String line : response.body().split("\\R")) {
            if (line.startsWith("data:")) {
                events.add(objectMapper.readTree(line.substring(5).trim()));
            }
        }
        return events;
    }

    private JsonNode confirm(String token, String confirmationToken) throws Exception {
        JsonNode response = postJson("/app/ai/appointments/confirm", token,
                Map.of("confirmationToken", confirmationToken));
        assertSuccess(response);
        return response.path("data");
    }

    private JsonNode appointments(String token) throws Exception {
        HttpRequest request = request("/app/appointment/listItem", token).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode result = objectMapper.readTree(response.body());
        assertSuccess(result);
        return result.path("data");
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        HttpRequest request = request(path, token)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("access-token", token);
        }
        return builder;
    }

    private JsonNode event(List<JsonNode> events, String type) {
        return events.stream()
                .filter(item -> type.equals(item.path("type").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing SSE event: " + type));
    }

    private void assertSuccess(JsonNode response) {
        assertThat(response.path("code").asInt()).isEqualTo(200);
    }
}
