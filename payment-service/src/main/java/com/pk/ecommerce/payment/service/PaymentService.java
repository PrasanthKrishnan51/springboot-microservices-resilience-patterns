package com.pk.ecommerce.payment.service;

import com.pk.ecommerce.common.constants.KafkaTopics;
import com.pk.ecommerce.common.dto.ApiResponse;
import com.pk.ecommerce.common.event.PaymentRequestedEvent;
import com.pk.ecommerce.common.event.PaymentResultEvent;
import com.pk.ecommerce.payment.model.Payment;
import com.pk.ecommerce.payment.model.PaymentStatus;
import com.pk.ecommerce.payment.repository.PaymentRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository repo;
    private final KafkaTemplate<String, Object> kafka;

    /**
     * Process payment against external payment gateway.
     * <p>
     * Resilience chain (outermost → innermost):
     * Bulkhead → CircuitBreaker → RateLimiter → Retry → TimeLimiter → call
     * <p>
     * This order is critical:
     * - Bulkhead:        gate BEFORE opening the circuit (no point checking if saturated)
     * - CircuitBreaker:  skip if gateway known-down
     * - RateLimiter:     don't exceed gateway's rate limits
     * - Retry:           retry transient failures (but NOT idempotency-unsafe operations without care)
     * - TimeLimiter:     cancel if gateway hangs
     */
    @Bulkhead(name = "paymentGatewayBulkhead", fallbackMethod = "paymentFallback")
    @CircuitBreaker(name = "paymentGatewayCB", fallbackMethod = "paymentFallback")
    @RateLimiter(name = "paymentGatewayLimiter", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentGatewayRetry", fallbackMethod = "paymentFallback")
    @TimeLimiter(name = "paymentGatewayTimeout")
    @Transactional
    public CompletableFuture<ApiResponse<String>> processPayment(
            String orderId, BigDecimal amount, String currency, String method) {

        log.info("Processing payment orderId={} amount={} {}", orderId, amount, currency);

        return CompletableFuture.supplyAsync(() -> {
            // Simulate external gateway call
            simulateGatewayDelay();

            Payment payment = Payment.builder()
                    .orderId(orderId).amount(amount).currency(currency)
                    .paymentMethod(method).status(PaymentStatus.PROCESSING)
                    .transactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .build();
            payment = repo.save(payment);

            // Simulate gateway response (90% success)
            boolean success = ThreadLocalRandom.current().nextDouble() < 0.90;

            if (success) {
                payment.setStatus(PaymentStatus.COMPLETED);
                repo.save(payment);

                PaymentResultEvent ok = PaymentResultEvent.builder()
                        .paymentId(payment.getId()).orderId(orderId)
                        .amount(amount).status("COMPLETED")
                        .transactionId(payment.getTransactionId()).build();
                kafka.send(KafkaTopics.PAYMENT_COMPLETED, orderId, ok);
                log.info("Payment COMPLETED orderId={} txn={}", orderId, payment.getTransactionId());
                return ApiResponse.ok(payment.getTransactionId(), "Payment successful");
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Gateway declined");
                repo.save(payment);

                PaymentResultEvent failed = PaymentResultEvent.builder()
                        .paymentId(payment.getId()).orderId(orderId)
                        .amount(amount).status("FAILED")
                        .failureReason("Gateway declined").build();
                kafka.send(KafkaTopics.PAYMENT_FAILED, orderId, failed);
                log.warn("Payment FAILED orderId={}", orderId);
                return ApiResponse.error("Payment declined by gateway", "PAYMENT_DECLINED");
            }
        });
    }

    /**
     * Saga compensation – refund a completed payment
     */
    @CircuitBreaker(name = "paymentGatewayCB", fallbackMethod = "refundFallback")
    @Retry(name = "paymentGatewayRetry", fallbackMethod = "refundFallback")
    @Transactional
    public ApiResponse<String> refundPayment(String orderId) {
        Payment payment = repo.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            return ApiResponse.error("Payment not in COMPLETED state", "INVALID_REFUND");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        repo.save(payment);

        PaymentResultEvent refund = PaymentResultEvent.builder()
                .paymentId(payment.getId()).orderId(orderId)
                .amount(payment.getAmount()).status("REFUNDED").build();
        kafka.send(KafkaTopics.PAYMENT_REFUND, orderId, refund);
        log.info("Payment REFUNDED orderId={}", orderId);
        return ApiResponse.ok("Refund processed", "Refund issued successfully");
    }

    // Fallbacks
    public CompletableFuture<ApiResponse<String>> paymentFallback(
            String orderId, BigDecimal amount, String currency, String method, Throwable ex) {
        log.error("[Fallback] Payment gateway unavailable orderId={} cause={}", orderId, ex.getMessage());

        PaymentRequestedEvent queued = PaymentRequestedEvent.builder()
                .orderId(orderId).amount(amount).currency(currency).paymentMethod(method).build();
        kafka.send(KafkaTopics.PAYMENT_REQUESTED, orderId, queued);

        return CompletableFuture.completedFuture(
                ApiResponse.fallback("Payment gateway unavailable. Payment queued for retry."));
    }

    public ApiResponse<String> refundFallback(String orderId, Throwable ex) {
        log.error("[Fallback] Refund failed orderId={}: {}", orderId, ex.getMessage());
        return ApiResponse.fallback("Refund gateway unavailable. Refund request logged for manual processing.");
    }

    private void simulateGatewayDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(100, 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
