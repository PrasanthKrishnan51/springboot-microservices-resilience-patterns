package com.pk.ecommerce.order.kafka;

import com.pk.ecommerce.common.constants.KafkaTopics;
import com.pk.ecommerce.common.event.InventoryResultEvent;
import com.pk.ecommerce.common.event.PaymentResultEvent;
import com.pk.ecommerce.order.model.OrderStatus;
import com.pk.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes results from inventory and payment services.
 *
 * @RetryableTopic:
 *   Spring Kafka feature that automatically creates retry topics
 *   (e.g. topic-retry-0, topic-retry-1) and a DLT.
 *   On failure the message moves through retry topics with backoff,
 *   eventually landing in the DLT if all retries are exhausted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderService orderService;

    // ── Inventory Result ──────────────────────────────────────────
    @RetryableTopic(
            attempts       = "3",
            backoff        = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10_000),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true"
    )
    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED, groupId = "order-service-group")
    public void onInventoryResult(
            @Payload InventoryResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int    partition,
            @Header(KafkaHeaders.OFFSET)             long   offset) {

        log.info("[Kafka←] InventoryResult orderId={} status={} topic={} p={} o={}",
                event.getOrderId(), event.getStatus(), topic, partition, offset);

        OrderStatus newStatus = "RESERVED".equals(event.getStatus())
                ? OrderStatus.INVENTORY_CONFIRMED
                : OrderStatus.CANCELLED;

        orderService.updateOrderStatus(event.getOrderId(), newStatus);

        if ("RESERVED".equals(event.getStatus())) {
            log.info("Inventory confirmed for orderId={} – ready for payment", event.getOrderId());
            // Trigger payment (in real impl: call payment service or publish event)
        } else {
            log.warn("Inventory FAILED for orderId={} – {}", event.getOrderId(), event.getFailureReason());
        }
    }

    // ── Payment Result ────────────────────────────────────────────
    @RetryableTopic(
            attempts       = "3",
            backoff        = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20_000),
            dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "order-service-group")
    public void onPaymentCompleted(
            @Payload PaymentResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.info("[Kafka←] PaymentResult orderId={} status={}", event.getOrderId(), event.getStatus());

        OrderStatus newStatus = switch (event.getStatus()) {
            case "COMPLETED" -> OrderStatus.CONFIRMED;
            case "FAILED"    -> OrderStatus.FAILED;
            case "REFUNDED"  -> OrderStatus.REFUNDED;
            default          -> OrderStatus.PAYMENT_PENDING;
        };

        orderService.updateOrderStatus(event.getOrderId(), newStatus);
    }

    // ── Dead Letter Queue ─────────────────────────────────────────
    @KafkaListener(topics = KafkaTopics.ORDER_DLQ, groupId = "order-dlq-group")
    public void onDeadLetter(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("[DLQ] 💀 Dead letter received from topic={} – event={} – MANUAL INTERVENTION REQUIRED",
                topic, event);
        // In production: alert PagerDuty / Slack, save to DLQ audit table
    }
}
