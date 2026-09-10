package com.atguigu.lease.config.ai;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class AiModelAvailableCondition implements Condition, EnvironmentAware {

    private Environment environment;

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment source = environment == null ? context.getEnvironment() : environment;
        return StringUtils.hasText(source.getProperty("spring.ai.openai.api-key"));
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
