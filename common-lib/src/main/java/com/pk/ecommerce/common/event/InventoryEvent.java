package com.pk.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class InventoryEvent {
    private String eventId;
    private String productId;
    private String orderId;
    private Integer quantity;
    private String status;          // RESERVE | RESERVED | RELEASE | LOW_STOCK
    private Integer currentStock;
    private String correlationId;
    private LocalDateTime timestamp;
}
