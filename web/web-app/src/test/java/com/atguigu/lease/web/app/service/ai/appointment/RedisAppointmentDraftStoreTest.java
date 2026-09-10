package com.atguigu.lease.web.app.service.ai.appointment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAppointmentDraftStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private RedisAppointmentDraftStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        store = new RedisAppointmentDraftStore(redisTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void savesDraftWithRemainingTenMinuteTtlInUserNamespace() {
        AppointmentDraft draft = draft(7L);

        store.save(7L, "token", draft, NOW.plusSeconds(600));

        verify(values).set(eq("ai:appointment:draft:7:token"), any(String.class), eq(Duration.ofMinutes(10)));
    }

    @Test
    void sameTokenCannotReadAnotherUsersDraft() {
        when(values.setIfAbsent(
                "ai:appointment:draft:8:token:lock", "1", Duration.ofSeconds(30)))
                .thenReturn(true);
        when(values.get("ai:appointment:draft:8:token")).thenReturn(null);

        AppointmentDraftClaim claim = store.claim(8L, "token");

        assertThat(claim.status()).isEqualTo(AppointmentDraftClaim.Status.MISSING);
        verify(redisTemplate).delete("ai:appointment:draft:8:token:lock");
    }

    private AppointmentDraft draft(Long userId) {
        return new AppointmentDraft(userId, 930001L, 920001L, "张三", "13800000000",
                LocalDateTime.of(2026, 9, 3, 10, 0), null);
    }
}
