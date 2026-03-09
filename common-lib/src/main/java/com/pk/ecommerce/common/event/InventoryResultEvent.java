package com.pk.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResultEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String productId;
    private String orderId;
    private Integer quantity;
    private String status;          // RESERVED | FAILED | RELEASED
    private Integer remainingStock;
    private String failureReason;
    private String correlationId;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
