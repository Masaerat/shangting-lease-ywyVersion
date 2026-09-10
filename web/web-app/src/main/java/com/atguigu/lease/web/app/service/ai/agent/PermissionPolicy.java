package com.atguigu.lease.web.app.service.ai.agent;

import org.springframework.stereotype.Component;

@Component
public class PermissionPolicy {

    public void requireAllowed(String tool, AgentPermission permission, AgentContext context) {
        if (permission == AgentPermission.WRITE) {
            throw new AgentExecutionException(
                    "Agent is not allowed to execute write tool: " + tool, "PERMISSION_DENIED");
        }
        if (permission == AgentPermission.PREPARE && context.userId() == null) {
            throw new AgentExecutionException(
                    "Authenticated user is required for prepare tool: " + tool, "PERMISSION_DENIED");
        }
    }
}
