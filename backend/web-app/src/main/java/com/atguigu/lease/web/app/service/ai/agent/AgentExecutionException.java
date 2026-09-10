package com.atguigu.lease.web.app.service.ai.agent;

public class AgentExecutionException extends RuntimeException {

    private final String errorType;

    public AgentExecutionException(String message, String errorType) {
        super(message);
        this.errorType = errorType;
    }

    public AgentExecutionException(String message, String errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
