package com.pk.ecommerce.payment.model;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private String id;
    @NonNull
    private String orderId;
    @NonNull
    private String userId;
    @NonNull
    private BigDecimal amount;
    @NonNull
    private String currency;
    @NonNull
    private String paymentMethod;
    @NonNull
    private PaymentStatus status;
    private String transactionId;
    private String failureReason;
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
