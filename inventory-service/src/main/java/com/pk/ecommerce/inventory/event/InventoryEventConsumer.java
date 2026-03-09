package com.pk.ecommerce.inventory.event;

import com.pk.ecommerce.common.constants.KafkaTopics;
import com.pk.ecommerce.common.event.InventoryReserveEvent;
import com.pk.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;

@Slf4j
@org.springframework.stereotype.Component
@RequiredArgsConstructor
class InventoryEventConsumer {
    private final InventoryService inventoryService;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2.0), dltTopicSuffix = ".dlq")
    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVE, groupId = "inventory-service-group")
    public void onReserveRequest(
            @Payload InventoryReserveEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("[Kafka←] InventoryReserve productId={} orderId={}", event.getProductId(), event.getOrderId());
        inventoryService.reserveStock(event.getProductId(), event.getQuantity(), event.getOrderId());
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_RELEASE, groupId = "inventory-service-group")
    public void onReleaseRequest(@Payload InventoryReserveEvent event) {
        inventoryService.releaseStock(event.getProductId(), event.getQuantity(), event.getOrderId());
    }
}
