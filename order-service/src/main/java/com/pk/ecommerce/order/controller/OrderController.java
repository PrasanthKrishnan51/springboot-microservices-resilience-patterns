package com.pk.ecommerce.order.controller;

import com.pk.ecommerce.common.dto.ApiResponse;
import com.pk.ecommerce.order.dto.CreateOrderRequest;
import com.pk.ecommerce.order.dto.OrderResponse;
import com.pk.ecommerce.order.resilience.ResilienceStatusService;
import com.pk.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ResilienceStatusService resilienceStatus;

    /**
     * POST /api/v1/orders – create order (rate-limited)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        ApiResponse<OrderResponse> response = orderService.createOrder(request);
        HttpStatus status = response.isSuccess() ? HttpStatus.ACCEPTED : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * GET /api/v1/orders/{id}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    /**
     * GET /api/v1/orders/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getUserOrders(
            @PathVariable String userId) {
        return ResponseEntity.ok(orderService.getUserOrders(userId));
    }

    /**
     * DELETE /api/v1/orders/{id}
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Customer requested") String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, reason));
    }

    /**
     * POST /api/v1/orders/{id}/check-inventory (manual trigger for demo)
     */
    @PostMapping("/{orderId}/check-inventory")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> checkInventory(
            @PathVariable String orderId,
            @RequestParam String productId,
            @RequestParam Integer quantity) {
        return orderService.checkInventory(orderId, productId, quantity)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * POST /api/v1/orders/{id}/pay (manual trigger for demo)
     */
    @PostMapping("/{orderId}/pay")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> processPayment(
            @PathVariable String orderId,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "INR") String currency,
            @RequestParam(defaultValue = "CARD") String paymentMethod) {
        return orderService.processPayment(orderId, amount, currency, paymentMethod)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/orders/resilience/status – live circuit breaker dashboard
     */
    @GetMapping("/resilience/status")
    public ResponseEntity<Map<String, Object>> resilienceStatus() {
        return ResponseEntity.ok(resilienceStatus.getFullStatus());
    }
}
