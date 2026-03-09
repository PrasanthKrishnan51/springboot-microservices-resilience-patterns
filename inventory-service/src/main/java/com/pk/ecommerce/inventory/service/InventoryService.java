package com.pk.ecommerce.inventory.service;

import com.pk.ecommerce.common.constants.KafkaTopics;
import com.pk.ecommerce.common.dto.ApiResponse;
import com.pk.ecommerce.common.event.InventoryReserveEvent;
import com.pk.ecommerce.common.event.InventoryResultEvent;
import com.pk.ecommerce.inventory.model.Inventory;
import com.pk.ecommerce.inventory.repository.InventoryRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository repo;
    private final KafkaTemplate<String, Object> kafka;

    /**
     * Reserve stock – protected by CircuitBreaker + Retry + Bulkhead.
     * If a downstream DB or external WMS is involved, this prevents cascade failure.
     */
    @CircuitBreaker(name = "wmsCB",fallbackMethod = "reserveFallback")
    @Retry(name = "wmsRetry",     fallbackMethod = "reserveFallback")
    @Bulkhead(name = "wmsBulkhead",  fallbackMethod = "reserveFallback")
    @Transactional
    public ApiResponse<String> reserveStock(String productId, Integer quantity, String orderId) {
        log.info("Reserving stock productId={} qty={} orderId={}", productId, quantity, orderId);

        Inventory inv = repo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not in inventory: " + productId));

        if (inv.getAvailableStock() < quantity) {
            String msg = String.format("Insufficient stock. Available=%d, Requested=%d",
                    inv.getAvailableStock(), quantity);
            log.warn(msg);

            // Publish FAILED result back
            InventoryResultEvent failed = InventoryResultEvent.builder()
                    .productId(productId).orderId(orderId).quantity(quantity)
                    .status("FAILED").remainingStock(inv.getAvailableStock())
                    .failureReason(msg).build();
            kafka.send(KafkaTopics.INVENTORY_RESERVED, orderId, failed);

            if (inv.getAvailableStock() < 10) {
                kafka.send(KafkaTopics.INVENTORY_LOW_STOCK, productId, inv);
            }
            return ApiResponse.error(msg, "INSUFFICIENT_STOCK");
        }

        inv.setReservedStock(inv.getReservedStock() + quantity);
        repo.save(inv);

        InventoryResultEvent ok = InventoryResultEvent.builder()
                .productId(productId).orderId(orderId).quantity(quantity)
                .status("RESERVED").remainingStock(inv.getAvailableStock()).build();
        kafka.send(KafkaTopics.INVENTORY_RESERVED, orderId, ok);

        log.info("Stock reserved productId={} orderId={} remaining={}", productId, orderId, inv.getAvailableStock());
        return ApiResponse.ok("Stock reserved", "Stock reserved successfully");
    }

    @Transactional
    public ApiResponse<String> releaseStock(String productId, Integer quantity, String orderId) {
        repo.findById(productId).ifPresent(inv -> {
            inv.setReservedStock(Math.max(0, inv.getReservedStock() - quantity));
            repo.save(inv);
            log.info("Stock released productId={} orderId={} qty={}", productId, orderId, quantity);
        });
        return ApiResponse.ok("Stock released");
    }

    @RateLimiter(name = "inventoryReadLimiter", fallbackMethod = "checkFallback")
    public ApiResponse<Boolean> checkAvailability(String productId, Integer quantity) {
        return repo.findById(productId)
                .map(inv -> ApiResponse.ok(inv.getAvailableStock() >= quantity))
                .orElse(ApiResponse.error("Product not found", "PRODUCT_NOT_FOUND"));
    }

    // Fallbacks
    public ApiResponse<String> reserveFallback(String productId, Integer qty, String orderId, Throwable ex) {
        log.error("[Fallback] WMS unavailable for productId={} – queuing reserve via Kafka", productId);
        InventoryReserveEvent event = InventoryReserveEvent.builder()
                .productId(productId).orderId(orderId).quantity(qty).build();
        kafka.send(KafkaTopics.INVENTORY_RESERVE, orderId, event);
        return ApiResponse.fallback("WMS unavailable – reservation queued asynchronously");
    }

    public ApiResponse<Boolean> checkFallback(String productId, Integer qty, Throwable ex) {
        log.warn("[RateLimiter] Inventory check rate limited for productId={}", productId);
        return ApiResponse.error("Inventory check temporarily unavailable", "INVENTORY_RATE_LIMITED");
    }
}
