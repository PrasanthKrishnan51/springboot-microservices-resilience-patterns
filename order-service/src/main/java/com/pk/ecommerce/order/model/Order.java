package com.pk.ecommerce.order.model;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private String id;

    @NonNull
    private String userId;

    @NonNull
    private OrderStatus status;

    @NonNull
    private BigDecimal totalAmount;

    @NonNull
    private String currency;

    @NonNull
    private String shippingAddress;

    private String paymentId;
    private String correlationId;
    private String cancelReason;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
