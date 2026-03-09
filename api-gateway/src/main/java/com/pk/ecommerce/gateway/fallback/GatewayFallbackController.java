package com.pk.ecommerce.gateway.fallback;

import com.pk.ecommerce.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway forwards here when a circuit breaker opens.
 * Each method returns a meaningful degraded response instead of a raw 503.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    @RequestMapping("/order")
    public Mono<ResponseEntity<ApiResponse<Void>>> orderFallback() {
        log.warn("[Gateway] Circuit OPEN → order-service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Order service is temporarily unavailable. Your cart is safe – please retry in a moment.",
                        "ORDER_SERVICE_UNAVAILABLE")));
    }

    @RequestMapping("/product")
    public Mono<ResponseEntity<ApiResponse<Void>>> productFallback() {
        log.warn("[Gateway] Circuit OPEN → product-service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Product catalog is temporarily unavailable. Please try again shortly.",
                        "PRODUCT_SERVICE_UNAVAILABLE")));
    }

    @RequestMapping("/inventory")
    public Mono<ResponseEntity<ApiResponse<Void>>> inventoryFallback() {
        log.warn("[Gateway] Circuit OPEN → inventory-service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Inventory check unavailable. Please retry before placing your order.",
                        "INVENTORY_SERVICE_UNAVAILABLE")));
    }

    @RequestMapping("/payment")
    public Mono<ResponseEntity<ApiResponse<Void>>> paymentFallback() {
        log.warn("[Gateway] Circuit OPEN → payment-service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Payment service is temporarily unavailable. No charge has been made.",
                        "PAYMENT_SERVICE_UNAVAILABLE")));
    }

    @RequestMapping("/user")
    public Mono<ResponseEntity<ApiResponse<Void>>> userFallback() {
        log.warn("[Gateway] Circuit OPEN → user-service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "User service is temporarily unavailable.",
                        "USER_SERVICE_UNAVAILABLE")));
    }
}
