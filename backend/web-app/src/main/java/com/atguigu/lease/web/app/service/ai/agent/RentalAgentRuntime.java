package com.atguigu.lease.web.app.service.ai.agent;

public interface RentalAgentRuntime {

    boolean available();

    AgentResult execute(AgentContext context);
}
