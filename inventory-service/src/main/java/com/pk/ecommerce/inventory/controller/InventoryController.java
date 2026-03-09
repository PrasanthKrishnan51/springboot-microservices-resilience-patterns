package com.pk.ecommerce.inventory.controller;

import com.pk.ecommerce.common.dto.ApiResponse;
import com.pk.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory") @RequiredArgsConstructor
class InventoryController {
    private final InventoryService service;

    @GetMapping("/{productId}/check")
    public ResponseEntity<ApiResponse<Boolean>> check(
            @PathVariable String productId, @RequestParam Integer quantity) {
        return ResponseEntity.ok(service.checkAvailability(productId, quantity));
    }

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<ApiResponse<String>> reserve(
            @PathVariable String productId,
            @RequestParam Integer quantity,
            @RequestParam String orderId) {
        return ResponseEntity.ok(service.reserveStock(productId, quantity, orderId));
    }

    @PostMapping("/{productId}/release")
    public ResponseEntity<ApiResponse<String>> release(
            @PathVariable String productId,
            @RequestParam Integer quantity,
            @RequestParam String orderId) {
        return ResponseEntity.ok(service.releaseStock(productId, quantity, orderId));
    }
}
