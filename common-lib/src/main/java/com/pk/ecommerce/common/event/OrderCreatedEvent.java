package com.pk.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/* ─── Order ──────────────────────────────────────────────────────── */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String orderId;
    private String userId;
    private BigDecimal totalAmount;
    private String currency;
    private List<OrderItemDto> items;
    private String shippingAddress;
    private String correlationId;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
