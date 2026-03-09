package com.pk.ecommerce.order.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Attaches event listeners to every Resilience4j instance at startup.
 * All state transitions and failures are logged clearly for ops visibility.
 *
 * This class is OPTIONAL but highly recommended in production because
 * the logs give you:
 *   - Exactly WHEN a circuit breaker opened/closed
 *   - HOW MANY retries are happening per second
 *   - WHICH bulkhead is rejecting calls (concurrency pressure)
 *   - WHICH rate-limiter is throttling outbound calls
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilienceEventLogger {

    private final CircuitBreakerRegistry cbRegistry;
    private final RetryRegistry          retryRegistry;
    private final BulkheadRegistry       bulkheadRegistry;
    private final RateLimiterRegistry    rateLimiterRegistry;

    @PostConstruct
    public void attachListeners() {
        attachCircuitBreakerListeners();
        attachRetryListeners();
        attachBulkheadListeners();
        attachRateLimiterListeners();
        log.info("✅ Resilience4j event listeners registered for all instances");
    }

    // ── Circuit Breaker ───────────────────────────────────────────
    private void attachCircuitBreakerListeners() {
        cbRegistry.getAllCircuitBreakers().forEach(cb -> {
            String name = cb.getName();

            cb.getEventPublisher()
              .onStateTransition(e -> {
                  var t = e.getStateTransition();
                  log.warn("🔌 [CircuitBreaker:{}] {} → {}",
                          name, t.getFromState(), t.getToState());
              })
              .onCallNotPermitted(e ->
                  log.warn("🚫 [CircuitBreaker:{}] CALL REJECTED – circuit is OPEN", name))
              .onError(e ->
                  log.error("❌ [CircuitBreaker:{}] Call FAILED in {}ms – {}",
                          name, e.getElapsedDuration().toMillis(), e.getThrowable().getMessage()))
              .onSlowCallRateExceeded(e ->
                  log.warn("🐢 [CircuitBreaker:{}] Slow call rate {:.1f}% exceeded threshold",
                          name, e.getSlowCallRate()))
              .onFailureRateExceeded(e ->
                  log.warn("📈 [CircuitBreaker:{}] Failure rate {:.1f}% exceeded threshold",
                          name, e.getFailureRate()))
              .onSuccess(e ->
                  log.debug("✔ [CircuitBreaker:{}] Call succeeded in {}ms",
                          name, e.getElapsedDuration().toMillis()))
              .onIgnoredError(e ->
                  log.debug("⚠ [CircuitBreaker:{}] Ignored error: {}",
                          name, e.getThrowable().getMessage()));
        });
    }

    // ── Retry ─────────────────────────────────────────────────────
    private void attachRetryListeners() {
        retryRegistry.getAllRetries().forEach(retry -> {
            String name = retry.getName();

            retry.getEventPublisher()
                 .onRetry(e ->
                     log.warn("🔁 [Retry:{}] Attempt #{} – last error: {}",
                             name, e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()))
                 .onSuccess(e ->
                     log.info("✅ [Retry:{}] Succeeded after {} attempt(s)",
                             name, e.getNumberOfRetryAttempts()))
                 .onError(e ->
                     log.error("💀 [Retry:{}] All {} attempts exhausted – {}",
                             name, e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()))
                 .onIgnoredError(e ->
                     log.debug("⚠ [Retry:{}] Error ignored (non-retryable): {}",
                             name, e.getLastThrowable().getClass().getSimpleName()));
        });
    }

    // ── Bulkhead ──────────────────────────────────────────────────
    private void attachBulkheadListeners() {
        bulkheadRegistry.getAllBulkheads().forEach(bh -> {
            String name = bh.getName();

            bh.getEventPublisher()
              .onCallPermitted(e ->
                  log.debug("🟢 [Bulkhead:{}] Call PERMITTED (available={})",
                          name, bh.getMetrics().getAvailableConcurrentCalls()))
              .onCallRejected(e ->
                  log.warn("🔴 [Bulkhead:{}] Call REJECTED – max concurrent calls reached (max={})",
                          name, bh.getMetrics().getMaxAllowedConcurrentCalls()))
              .onCallFinished(e ->
                  log.debug("🔵 [Bulkhead:{}] Call FINISHED (available={})",
                          name, bh.getMetrics().getAvailableConcurrentCalls()));
        });
    }

    // ── Rate Limiter ──────────────────────────────────────────────
    private void attachRateLimiterListeners() {
        rateLimiterRegistry.getAllRateLimiters().forEach(rl -> {
            String name = rl.getName();

            rl.getEventPublisher()
              .onSuccess(e ->
                  log.debug("⚡ [RateLimiter:{}] Permit ACQUIRED (available={})",
                          name, rl.getMetrics().getAvailablePermissions()))
              .onFailure(e ->
                  log.warn("⛔ [RateLimiter:{}] Permit DENIED – rate limit hit (waiting={})",
                          name, rl.getMetrics().getNumberOfWaitingThreads()));
        });
    }
}
