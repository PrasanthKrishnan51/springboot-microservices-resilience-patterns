package com.pk.ecommerce.payment.controller;

import com.pk.ecommerce.common.dto.ApiResponse;
import com.pk.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
class PaymentController {
    private final PaymentService service;

    @PostMapping("/process")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> process(
            @RequestParam String orderId,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "INR") String currency,
            @RequestParam(defaultValue = "CARD") String method) {
        return service.processPayment(orderId, amount, currency, method)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<ApiResponse<String>> refund(@PathVariable String orderId) {
        return ResponseEntity.ok(service.refundPayment(orderId));
    }
}
