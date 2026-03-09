package com.pk.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PaymentEvent {
    private String        eventId;
    private String        paymentId;
    private String        orderId;
    private String        userId;
    private BigDecimal amount;
    private String        currency;
    private String        status;          // REQUESTED | COMPLETED | FAILED | REFUNDED
    private String        paymentMethod;
    private String        failureReason;
    private String        correlationId;
    private LocalDateTime timestamp;
}