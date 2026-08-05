package com.atguigu.lease.web.app.service.ai.appointment;

import com.atguigu.lease.common.utils.JsonUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class RedisAppointmentDraftStore implements AppointmentDraftStore {

    private static final String KEY_PREFIX = "ai:appointment:draft:";
    private static final Duration CLAIM_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisAppointmentDraftStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.systemUTC());
    }

    RedisAppointmentDraftStore(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public void save(Long userId, String token, AppointmentDraft draft, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Draft expiration must be in the future");
        }
        redisTemplate.opsForValue().set(key(userId, token), JsonUtil.toJsonString(draft), ttl);
    }

    @Override
    public AppointmentDraftClaim claim(Long userId, String token) {
        String lockKey = lockKey(userId, token);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", CLAIM_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return AppointmentDraftClaim.processing();
        }
        String json = redisTemplate.opsForValue().get(key(userId, token));
        if (json == null) {
            redisTemplate.delete(lockKey);
            return AppointmentDraftClaim.missing();
        }
        return AppointmentDraftClaim.claimed(JsonUtil.parseObject(json, AppointmentDraft.class));
    }

    @Override
    public void complete(Long userId, String token) {
        redisTemplate.delete(java.util.List.of(key(userId, token), lockKey(userId, token)));
    }

    @Override
    public void release(Long userId, String token) {
        redisTemplate.delete(lockKey(userId, token));
    }

    static String key(Long userId, String token) {
        return KEY_PREFIX + userId + ":" + token;
    }

    private static String lockKey(Long userId, String token) {
        return key(userId, token) + ":lock";
    }
}
