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
public class OrderStatusChangedEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String orderId;
    private String userId;
    private String previousStatus;
    private String newStatus;
    private String reason;
    private String correlationId;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
