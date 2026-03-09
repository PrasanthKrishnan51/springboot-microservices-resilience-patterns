package com.pk.ecommerce.order.model;

public enum OrderStatus {
    PENDING,
    INVENTORY_CHECKING,
    INVENTORY_CONFIRMED,
    PAYMENT_PENDING,
    PAYMENT_PROCESSING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED,
    FAILED
}
