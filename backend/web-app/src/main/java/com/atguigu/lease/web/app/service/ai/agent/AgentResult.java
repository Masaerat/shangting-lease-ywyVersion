package com.atguigu.lease.web.app.service.ai.agent;

import java.util.List;

public record AgentResult(
        String answer,
        String model,
        String traceId,
        List<AgentObservation> observations,
        List<AgentStep> trajectory) {

    public AgentResult {
        answer = answer == null ? "" : answer;
        observations = observations == null ? List.of() : List.copyOf(observations);
        trajectory = trajectory == null ? List.of() : List.copyOf(trajectory);
    }
}
