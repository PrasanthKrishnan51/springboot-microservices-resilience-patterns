package com.pk.ecommerce.common.constants;

/**
 * Central registry of ALL Kafka topic names used across the platform.
 * Keeping them here prevents typos and makes refactoring safe.
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    //  Order topics 
    public static final String ORDER_CREATED          = "ecom.order.created";
    public static final String ORDER_CONFIRMED        = "ecom.order.confirmed";
    public static final String ORDER_CANCELLED        = "ecom.order.cancelled";
    public static final String ORDER_COMPLETED        = "ecom.order.completed";
    public static final String ORDER_DLQ              = "ecom.order.dlq";

    //  Payment topics 
    public static final String PAYMENT_REQUESTED      = "ecom.payment.requested";
    public static final String PAYMENT_COMPLETED      = "ecom.payment.completed";
    public static final String PAYMENT_FAILED         = "ecom.payment.failed";
    public static final String PAYMENT_REFUND         = "ecom.payment.refund";
    public static final String PAYMENT_DLQ            = "ecom.payment.dlq";

    //  Inventory topics 
    public static final String INVENTORY_RESERVE      = "ecom.inventory.reserve";
    public static final String INVENTORY_RESERVED     = "ecom.inventory.reserved";
    public static final String INVENTORY_RELEASE      = "ecom.inventory.release";
    public static final String INVENTORY_LOW_STOCK    = "ecom.inventory.low-stock";
    public static final String INVENTORY_DLQ          = "ecom.inventory.dlq";

    //  Notification topics 
    public static final String NOTIFICATION_EMAIL     = "ecom.notification.email";
    public static final String NOTIFICATION_SMS       = "ecom.notification.sms";
    public static final String NOTIFICATION_PUSH      = "ecom.notification.push";

    //  Product topics 
    public static final String PRODUCT_PRICE_CHANGED  = "ecom.product.price-changed";
    public static final String PRODUCT_CREATED        = "ecom.product.created";
}
