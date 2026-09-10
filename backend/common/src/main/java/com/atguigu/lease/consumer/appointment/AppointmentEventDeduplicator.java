package com.atguigu.lease.consumer.appointment;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AppointmentEventDeduplicator {

    private static final String PREFIX = "appointment:consumer:";
    private static final Duration CLAIM_TTL = Duration.ofMinutes(5);
    private static final Duration PROCESSED_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;

    public AppointmentEventDeduplicator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryClaim(Long eventId) {
        if (eventId == null) {
            return true;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey(eventId)))) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(claimKey(eventId), "1", CLAIM_TTL));
    }

    public void markProcessed(Long eventId) {
        if (eventId == null) {
            return;
        }
        redisTemplate.opsForValue().set(processedKey(eventId), "1", PROCESSED_TTL);
        redisTemplate.delete(claimKey(eventId));
    }

    public void release(Long eventId) {
        if (eventId != null) {
            redisTemplate.delete(claimKey(eventId));
        }
    }

    private static String processedKey(Long eventId) {
        return PREFIX + "processed:" + eventId;
    }

    private static String claimKey(Long eventId) {
        return PREFIX + "claim:" + eventId;
    }
}
