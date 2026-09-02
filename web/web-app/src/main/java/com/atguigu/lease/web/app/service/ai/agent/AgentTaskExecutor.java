package com.atguigu.lease.web.app.service.ai.agent;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AgentTaskExecutor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> T call(Callable<T> task, Duration timeout, String timeoutType) {
        Future<T> future = executor.submit(task);
        try {
            return future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AgentExecutionException("Agent operation timed out", timeoutType, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentExecutionException("Agent operation was interrupted", "INTERRUPTED", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AgentExecutionException("Agent operation failed", "EXECUTION_ERROR", cause);
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
