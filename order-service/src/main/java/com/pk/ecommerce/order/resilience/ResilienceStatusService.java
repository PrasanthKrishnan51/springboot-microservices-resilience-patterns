package com.pk.ecommerce.order.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides a rich status snapshot of every resilience component.
 * Exposed via GET /api/v1/orders/resilience/status
 */
@Service
@RequiredArgsConstructor
public class ResilienceStatusService {

    private final CircuitBreakerRegistry cbRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public Map<String, Object> getFullStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("circuitBreakers", circuitBreakerStatus());
        status.put("retries", retryStatus());
        status.put("bulkheads", bulkheadStatus());
        status.put("rateLimiters", rateLimiterStatus());
        return status;
    }

    private Map<String, Object> circuitBreakerStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        cbRegistry.getAllCircuitBreakers().forEach(cb -> {
            CircuitBreaker.Metrics m = cb.getMetrics();
            map.put(cb.getName(), Map.of(
                    "state", cb.getState().name(),
                    "failureRate", String.format("%.1f%%", m.getFailureRate()),
                    "slowCallRate", String.format("%.1f%%", m.getSlowCallRate()),
                    "successfulCalls", m.getNumberOfSuccessfulCalls(),
                    "failedCalls", m.getNumberOfFailedCalls(),
                    "notPermittedCalls", m.getNumberOfNotPermittedCalls(),
                    "bufferedCalls", m.getNumberOfBufferedCalls()
            ));
        });
        return map;
    }

    private Map<String, Object> retryStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        retryRegistry.getAllRetries().forEach(retry -> {
            var m = retry.getMetrics();
            map.put(retry.getName(), Map.of(
                    "successWithoutRetry", m.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                    "successWithRetry", m.getNumberOfSuccessfulCallsWithRetryAttempt(),
                    "failedWithoutRetry", m.getNumberOfFailedCallsWithoutRetryAttempt(),
                    "failedWithRetry", m.getNumberOfFailedCallsWithRetryAttempt()
            ));
        });
        return map;
    }

    private Map<String, Object> bulkheadStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        bulkheadRegistry.getAllBulkheads().forEach(bh -> {
            var m = bh.getMetrics();
            map.put(bh.getName(), Map.of(
                    "availableConcurrentCalls", m.getAvailableConcurrentCalls(),
                    "maxAllowedConcurrentCalls", m.getMaxAllowedConcurrentCalls()
            ));
        });
        return map;
    }

    private Map<String, Object> rateLimiterStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        rateLimiterRegistry.getAllRateLimiters().forEach(rl -> {
            var m = rl.getMetrics();
            map.put(rl.getName(), Map.of(
                    "availablePermissions", m.getAvailablePermissions(),
                    "numberOfWaitingThreads", m.getNumberOfWaitingThreads()
            ));
        });
        return map;
    }
}
