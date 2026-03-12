package com.pk.ecommerce.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private String eventId;
    private String orderId;
    private String userId;
    private String status;          // CREATED | CONFIRMED | CANCELLED | COMPLETED
    private BigDecimal totalAmount;
    private String currency;
    private List<LineItem> items;
    private String correlationId;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
