package com.pk.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String paymentId;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String status;          // COMPLETED | FAILED | REFUNDED
    private String failureReason;
    private String transactionId;
    private String correlationId;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
