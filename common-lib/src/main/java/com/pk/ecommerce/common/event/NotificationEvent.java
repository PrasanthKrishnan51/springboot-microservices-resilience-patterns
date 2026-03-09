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
public class NotificationEvent {
    @Builder.Default private String eventId = UUID.randomUUID().toString();
    private String recipientId;
    private String recipientEmail;
    private String recipientPhone;
    private String channel;         // EMAIL | SMS | PUSH
    private String subject;
    private String body;
    private String templateId;
    private String referenceId;
    @Builder.Default private LocalDateTime timestamp = LocalDateTime.now();
}