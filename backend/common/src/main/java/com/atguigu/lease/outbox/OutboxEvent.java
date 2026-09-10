package com.atguigu.lease.outbox;

public record OutboxEvent(Long id, Long aggregateId, String eventType, String payloadJson, int attempts) {
}
