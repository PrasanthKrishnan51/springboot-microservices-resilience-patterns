package com.pk.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotBlank
    private String userId;
    @NotBlank
    private String shippingAddress;
    @NotBlank
    private String currency;
    @NotBlank
    private String paymentMethod;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
}



