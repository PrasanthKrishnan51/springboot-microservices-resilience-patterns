package com.pk.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/* ─── Payment ────────────────────────────────────────────────────── */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestedEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String paymentId;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;   // CARD | UPI | NETBANKING | WALLET
    private String correlationId;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
