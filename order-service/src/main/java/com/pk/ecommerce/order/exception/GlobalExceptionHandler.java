package com.pk.ecommerce.order.exception;

import com.pk.ecommerce.common.dto.ApiResponse;
import com.pk.ecommerce.common.exception.EcommerceException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Translates every exception type (including Resilience4j) into a
 * structured ApiResponse with the correct HTTP status.
 *
 * ┌──────────────────────────────────┬──────────┬────────────────────────────┐
 * │ Exception                        │ Status   │ Cause                      │
 * ├──────────────────────────────────┼──────────┼────────────────────────────┤
 * │ CallNotPermittedException        │ 503      │ Circuit breaker is OPEN    │
 * │ BulkheadFullException            │ 429      │ Max concurrent calls hit   │
 * │ RequestNotPermitted              │ 429      │ Rate limit exceeded        │
 * │ OrderNotFoundException           │ 404      │ Order does not exist       │
 * │ OrderValidationException         │ 400      │ Business rule violation    │
 * │ MethodArgumentNotValidException  │ 400      │ Bean validation failure    │
 * │ Exception                        │ 500      │ Unexpected error           │
 * └──────────────────────────────────┴──────────┴────────────────────────────┘
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        log.warn("[CircuitBreaker] Call not permitted: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiResponse.error(
                        "Service temporarily unavailable. Circuit breaker is OPEN. Please retry in ~30 seconds.",
                        "CIRCUIT_BREAKER_OPEN"));
    }

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<ApiResponse<Void>> handleBulkheadFull(BulkheadFullException ex) {
        log.warn("[Bulkhead] Concurrent call limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                ApiResponse.error(
                        "System is at capacity. Too many concurrent requests. Please retry shortly.",
                        "BULKHEAD_FULL"));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RequestNotPermitted ex) {
        log.warn("[RateLimiter] Request denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                ApiResponse.rateLimited());
    }

    @ExceptionHandler(EcommerceException.class)
    public ResponseEntity<ApiResponse<Void>> handleEcommerceException(EcommerceException ex) {
        log.warn("[Business] {} - {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(
                ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(errors, "VALIDATION_ERROR"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("[Unexpected] {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(
                ApiResponse.error("An unexpected error occurred.", "INTERNAL_ERROR"));
    }
}
