package com.atguigu.lease.web.app.service.ai.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgentExecutionState {

    public static final String CONTEXT_KEY = "rentalAgentState";
    public static final String USER_ID_KEY = "rentalAgentUserId";

    private final String traceId = UUID.randomUUID().toString();
    private final List<AgentObservation> observations = new ArrayList<>();
    private final List<AgentStep> trajectory = new ArrayList<>();
    private final Map<String, Integer> toolCalls = new HashMap<>();
    private int nextStepNumber = 1;

    public String traceId() {
        return traceId;
    }

    public synchronized void registerToolRequest(String tool, int maxSameToolCalls) {
        int count = toolCalls.merge(tool, 1, Integer::sum);
        if (count > maxSameToolCalls) {
            throw new AgentExecutionException("Tool call limit exceeded: " + tool, "TOOL_CALL_LIMIT");
        }
    }

    public synchronized void recordObservation(String tool, Object payload) {
        observations.add(new AgentObservation(tool, payload));
    }

    public synchronized void recordStep(AgentStep step) {
        trajectory.add(step);
    }

    public synchronized int nextStepNumber() {
        return nextStepNumber++;
    }

    public synchronized List<AgentObservation> observations() {
        return List.copyOf(observations);
    }

    public synchronized List<AgentStep> trajectory() {
        return List.copyOf(trajectory);
    }
}
