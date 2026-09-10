package com.atguigu.lease.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.agent")
public class AiAgentProperties {

    private String primaryModel = "";
    private String fallbackModel = "";
    private int maxSteps = 6;
    private int maxSameToolCalls = 3;
    private long timeoutMs = 15_000;
    private long toolTimeoutMs = 3_000;
    private int maxToolResultChars = 8_000;
    private boolean trajectoryEnabled = true;
}
