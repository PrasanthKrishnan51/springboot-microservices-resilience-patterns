package com.pk.ecommerce.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public  class OrderItemRequest {
        @NotBlank
        private String productId;
        @NotBlank
        private String productName;
        @Min(1)
        @Max(1000)
        private Integer quantity;
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal unitPrice;
    }
