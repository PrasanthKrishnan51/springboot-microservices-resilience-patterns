package com.pk.ecommerce.order.model;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    private String id;

    private Order order;

    @NonNull
    private String productId;

    @NonNull
    private String productName;

    @NonNull
    private Integer quantity;

    @NonNull
    private BigDecimal unitPrice;

    @NonNull
    private BigDecimal subtotal;
}
