package com.atguigu.lease.web.app.service.ai.agent;

public record AgentStep(
        int step,
        String model,
        String tool,
        String status,
        long elapsedMs,
        Integer resultCount,
        String errorType) {
}
