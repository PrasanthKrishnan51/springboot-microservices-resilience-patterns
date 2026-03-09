package com.pk.ecommerce.order.service;

import com.pk.ecommerce.common.constants.KafkaTopics;
import com.pk.ecommerce.common.dto.ApiResponse;
import com.pk.ecommerce.order.dto.CreateOrderRequest;
import com.pk.ecommerce.order.dto.OrderItemResponse;
import com.pk.ecommerce.order.dto.OrderResponse;
import com.pk.ecommerce.order.exception.OrderNotFoundException;
import com.pk.ecommerce.order.exception.OrderValidationException;
import com.pk.ecommerce.order.kafka.OrderEventProducer;
import com.pk.ecommerce.order.model.Order;
import com.pk.ecommerce.order.model.OrderItem;
import com.pk.ecommerce.order.model.OrderStatus;
import com.pk.ecommerce.order.repository.OrderRepository;
import com.pk.ecommerce.common.event.InventoryReserveEvent;
import com.pk.ecommerce.common.event.OrderCreatedEvent;
import com.pk.ecommerce.common.event.OrderStatusChangedEvent;
import com.pk.ecommerce.common.event.PaymentRequestedEvent;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║         ORDER SERVICE – Resilience4j Showcase           ║
 * ╠══════════════════════════════════════════════════════════╣
 * ║  Pattern         │ Applied to                           ║
 * ║──────────────────┼───────────────────────────────────── ║
 * ║  @RateLimiter    │ createOrder   (entry point)          ║
 * ║  @CircuitBreaker │ inventory, payment, product calls    ║
 * ║  @Retry          │ inventory, payment, product calls    ║
 * ║  @Bulkhead       │ inventory, payment (semaphore)       ║
 * ║  @TimeLimiter    │ async inventory & payment calls      ║
 * ║  Fallback method │ every annotated method               ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * DECORATOR ORDER (Resilience4j applies outer→inner):
 *   Bulkhead → CircuitBreaker → RateLimiter → Retry → TimeLimiter → call
 *
 * This means:
 *  1. Bulkhead gates concurrent access first.
 *  2. CircuitBreaker blocks if the service is known-dead.
 *  3. RateLimiter throttles our own outbound call rate.
 *  4. Retry attempts the call up to N times on failure.
 *  5. TimeLimiter cancels if the call exceeds the deadline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository    orderRepository;
    private final OrderEventProducer eventProducer;
    private final WebClient          inventoryClient;
    private final WebClient          paymentClient;
    private final WebClient          productClient;

    // ══════════════════════════════════════════════════════════════
    //  1. CREATE ORDER
    //     @RateLimiter – caps how many orders this instance accepts
    //     per second. Prevents stampede during flash sales.
    // ══════════════════════════════════════════════════════════════
    @RateLimiter(name = "orderCreationLimiter", fallbackMethod = "orderCreationRateLimitFallback")
    @Transactional
    public ApiResponse<OrderResponse> createOrder(CreateOrderRequest request) {
        log.info("Creating order for userId={}", request.getUserId());

        // Basic business validation
        validateOrderRequest(request);

        BigDecimal total = request.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String correlationId = UUID.randomUUID().toString();

        Order order = Order.builder()
                .userId(request.getUserId())
                .totalAmount(total)
                .currency(request.getCurrency())
                .shippingAddress(request.getShippingAddress())
                .status(OrderStatus.PENDING)
                .correlationId(correlationId)
                .build();

        List<OrderItem> items = request.getItems().stream().map(i ->
                OrderItem.builder()
                        .order(order)
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .subtotal(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .build()
        ).toList();
        order.setItems(items);

        Order saved = orderRepository.save(order);

        // Publish ORDER_CREATED to Kafka → inventory-service and payment-service listen
        OrderCreatedEvent event = buildOrderCreatedEvent(saved, correlationId);
        eventProducer.publish(KafkaTopics.ORDER_CREATED, saved.getId(), event);

        log.info("Order created orderId={} correlationId={}", saved.getId(), correlationId);
        return ApiResponse.ok(mapToResponse(saved, "Order created. Processing started."));
    }

    // ══════════════════════════════════════════════════════════════
    //  2. CHECK INVENTORY
    //     @CircuitBreaker + @Retry + @Bulkhead + @TimeLimiter
    //
    //  WHY all four together?
    //  - TimeLimiter:    don't wait forever for inventory response
    //  - Retry:          transient network blips → retry with backoff
    //  - CircuitBreaker: if inventory is truly down, stop trying fast
    //  - Bulkhead:       cap concurrent inventory calls so other
    //                    orders don't starve
    // ══════════════════════════════════════════════════════════════
    @CircuitBreaker(name  = "inventoryServiceCB",       fallbackMethod = "inventoryFallback")
    @Retry          (name  = "inventoryServiceRetry",    fallbackMethod = "inventoryFallback")
    @Bulkhead       (name  = "inventoryServiceBulkhead", fallbackMethod = "inventoryFallback")
    @TimeLimiter    (name  = "inventoryTimeout")
    public CompletableFuture<ApiResponse<String>> checkInventory(String orderId, String productId, Integer quantity) {
        log.info("Checking inventory productId={} qty={} orderId={}", productId, quantity, orderId);

        return inventoryClient
                .get()
                .uri("/api/v1/inventory/{productId}/check?quantity={qty}", productId, quantity)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r  -> log.info("Inventory OK productId={}", productId))
                .doOnError (ex -> log.warn("Inventory call failed: {}", ex.getMessage()))
                .map(ApiResponse::ok)
                .toFuture();
    }

    // ══════════════════════════════════════════════════════════════
    //  3. PROCESS PAYMENT
    //     @CircuitBreaker + @Retry + @Bulkhead + @TimeLimiter
    //
    //  Payment is the most critical path:
    //  - CircuitBreaker failureRateThreshold is 30% (stricter than others)
    //  - waitDurationInOpenState is 60s (longer cool-down)
    //  - Retry maxAttempts is only 2 (avoid double-charging)
    // ══════════════════════════════════════════════════════════════
    @CircuitBreaker(name  = "paymentServiceCB",       fallbackMethod = "paymentFallback")
    @Retry          (name  = "paymentServiceRetry",    fallbackMethod = "paymentFallback")
    @Bulkhead       (name  = "paymentServiceBulkhead", fallbackMethod = "paymentFallback")
    @TimeLimiter    (name  = "paymentTimeout")
    @RateLimiter    (name  = "paymentGatewayLimiter",  fallbackMethod = "paymentFallback")
    public CompletableFuture<ApiResponse<String>> processPayment(
            String orderId, BigDecimal amount, String currency, String paymentMethod) {

        log.info("Processing payment orderId={} amount={} {}", orderId, amount, currency);

        PaymentRequestedEvent payload = PaymentRequestedEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .orderId(orderId)
                .amount(amount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .build();

        return paymentClient
                .post()
                .uri("/api/v1/payments/process")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r  -> updateOrderStatus(orderId, OrderStatus.PAYMENT_PROCESSING))
                .doOnError (ex -> log.error("Payment call error orderId={}: {}", orderId, ex.getMessage()))
                .map(ApiResponse::ok)
                .toFuture();
    }

    // ══════════════════════════════════════════════════════════════
    //  4. GET PRODUCT DETAILS
    //     @CircuitBreaker + @Retry + @Bulkhead
    //
    //  Product lookup is read-only → more retries allowed,
    //  lower cost of failure (show cached data instead).
    // ══════════════════════════════════════════════════════════════
    @CircuitBreaker(name = "productServiceCB",        fallbackMethod = "productFallback")
    @Retry          (name = "productServiceRetry",     fallbackMethod = "productFallback")
    @Bulkhead       (name = "productServiceBulkhead",  fallbackMethod = "productFallback")
    @TimeLimiter    (name = "productTimeout")
    public CompletableFuture<ApiResponse<String>> getProductDetails(String productId) {
        log.info("Fetching product details productId={}", productId);

        return productClient
                .get()
                .uri("/api/v1/products/{id}", productId)
                .retrieve()
                .bodyToMono(String.class)
                .map(ApiResponse::ok)
                .toFuture();
    }

    // ══════════════════════════════════════════════════════════════
    //  QUERY METHODS (no resilience decoration needed – local DB)
    // ══════════════════════════════════════════════════════════════
    public ApiResponse<OrderResponse> getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return ApiResponse.ok(mapToResponse(order, null));
    }

    public ApiResponse<List<OrderResponse>> getUserOrders(String userId) {
        List<OrderResponse> list = orderRepository.findByUserId(userId)
                .stream().map(o -> mapToResponse(o, null)).toList();
        return ApiResponse.ok(list);
    }

    @Transactional
    public ApiResponse<OrderResponse> cancelOrder(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new OrderValidationException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        orderRepository.save(order);

        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
                .orderId(orderId)
                .userId(order.getUserId())
                .previousStatus(order.getStatus().name())
                .newStatus("CANCELLED")
                .reason(reason)
                .correlationId(order.getCorrelationId())
                .build();
        eventProducer.publish(KafkaTopics.ORDER_CANCELLED, orderId, event);

        return ApiResponse.ok(mapToResponse(order, "Order cancelled"));
    }

    @Transactional
    public void updateOrderStatus(String orderId, OrderStatus status) {
        orderRepository.findById(orderId).ifPresent(o -> {
            o.setStatus(status);
            orderRepository.save(o);
            log.info("Order {} status → {}", orderId, status);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  FALLBACK METHODS
    //
    //  Rules:
    //  1. Same name as the protected method + "Fallback" suffix
    //     (or any name passed to fallbackMethod=)
    //  2. Must have the SAME parameters as the protected method
    //     PLUS a final Throwable parameter.
    //  3. Return type must match exactly.
    //  4. Each fallback logs the circuit state and returns a
    //     graceful degraded response.
    // ══════════════════════════════════════════════════════════════

    /** Fallback for @RateLimiter on createOrder */
    public ApiResponse<OrderResponse> orderCreationRateLimitFallback(
            CreateOrderRequest request, Throwable ex) {
        log.warn("[RateLimiter] Order creation rate limit hit for userId={}. Cause: {}",
                request.getUserId(), ex.getMessage());
        return ApiResponse.error(
                "Too many orders being placed right now. Please wait a moment and retry.",
                "ORDER_RATE_LIMIT_EXCEEDED");
    }

    /** Fallback for inventory check (CircuitBreaker / Retry / Bulkhead / TimeLimiter) */
    public CompletableFuture<ApiResponse<String>> inventoryFallback(
            String orderId, String productId, Integer quantity, Throwable ex) {
        log.warn("[Fallback] Inventory service unavailable for orderId={}. Cause: {} – {}",
                orderId, ex.getClass().getSimpleName(), ex.getMessage());

        // Update order status to reflect pending inventory check
        updateOrderStatus(orderId, OrderStatus.INVENTORY_CHECKING);

        // Publish event so inventory-service can process asynchronously
        InventoryReserveEvent event = InventoryReserveEvent.builder()
                .productId(productId)
                .orderId(orderId)
                .quantity(quantity)
                .build();
        eventProducer.publish(KafkaTopics.INVENTORY_RESERVE, orderId, event);

        return CompletableFuture.completedFuture(
                ApiResponse.fallback("Inventory service unavailable. Request queued via Kafka for async processing."));
    }

    /** Fallback for payment processing */
    public CompletableFuture<ApiResponse<String>> paymentFallback(
            String orderId, BigDecimal amount, String currency, String paymentMethod, Throwable ex) {
        log.error("[Fallback] Payment service unavailable for orderId={}. Cause: {} – {}",
                orderId, ex.getClass().getSimpleName(), ex.getMessage());

        updateOrderStatus(orderId, OrderStatus.PAYMENT_PENDING);

        PaymentRequestedEvent event = PaymentRequestedEvent.builder()
                .orderId(orderId).amount(amount).currency(currency)
                .paymentMethod(paymentMethod).build();
        eventProducer.publish(KafkaTopics.PAYMENT_REQUESTED, orderId, event);

        return CompletableFuture.completedFuture(
                ApiResponse.fallback("Payment service temporarily unavailable. Payment queued – no charge made yet."));
    }

    /** Fallback for product details */
    public CompletableFuture<ApiResponse<String>> productFallback(
            String productId, Throwable ex) {
        log.warn("[Fallback] Product service unavailable for productId={}. Serving cached/empty data. Cause: {}",
                productId, ex.getMessage());
        // In production: return from Redis cache
        return CompletableFuture.completedFuture(
                ApiResponse.ok("{\"cached\":true,\"productId\":\"" + productId + "\"}",
                        "Serving cached product data"));
    }

    // ══════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════

    private void validateOrderRequest(CreateOrderRequest req) {
        if (req.getItems().isEmpty()) {
            throw new OrderValidationException("Order must have at least one item");
        }
        // Guard: max 50 items per order
        if (req.getItems().size() > 50) {
            throw new OrderValidationException("Order cannot have more than 50 items");
        }
    }

    private OrderCreatedEvent buildOrderCreatedEvent(Order order, String correlationId) {
        List<OrderCreatedEvent.OrderItemDto> items = order.getItems().stream()
                .map(i -> OrderCreatedEvent.OrderItemDto.builder()
                        .productId(i.getProductId()).productName(i.getProductName())
                        .quantity(i.getQuantity()).unitPrice(i.getUnitPrice()).build())
                .toList();

        return OrderCreatedEvent.builder()
                .orderId(order.getId()).userId(order.getUserId())
                .totalAmount(order.getTotalAmount()).currency(order.getCurrency())
                .items(items).shippingAddress(order.getShippingAddress())
                .correlationId(correlationId).build();
    }

    private OrderResponse mapToResponse(Order o, String message) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> OrderItemResponse.builder()
                        .productId(i.getProductId()).productName(i.getProductName())
                        .quantity(i.getQuantity()).unitPrice(i.getUnitPrice()).subtotal(i.getSubtotal()).build())
                .toList();

        return OrderResponse.builder()
                .orderId(o.getId()).userId(o.getUserId())
                .status(o.getStatus().name()).totalAmount(o.getTotalAmount())
                .currency(o.getCurrency()).shippingAddress(o.getShippingAddress())
                .paymentId(o.getPaymentId()).correlationId(o.getCorrelationId())
                .items(items).createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt())
                .message(message).build();
    }
}
