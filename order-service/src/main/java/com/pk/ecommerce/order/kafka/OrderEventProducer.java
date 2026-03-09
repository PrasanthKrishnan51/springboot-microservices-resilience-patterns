package com.pk.ecommerce.order.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around KafkaTemplate.
 * Adds consistent logging, async callback handling, and DLQ fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String key, Object event) {
        log.info("[Kafka→] Publishing to topic={} key={} type={}",
                topic, key, event.getClass().getSimpleName());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Kafka✗] Failed to publish topic={} key={} – {}",
                        topic, key, ex.getMessage());
                publishToDlq(topic, key, event);
            } else {
                log.debug("[Kafka✓] Delivered topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private void publishToDlq(String originalTopic, String key, Object event) {
        String dlqTopic = originalTopic + ".dlq";
        try {
            kafkaTemplate.send(dlqTopic, key, event);
            log.warn("[Kafka DLQ] Sent to {} key={}", dlqTopic, key);
        } catch (Exception e) {
            log.error("[Kafka CRITICAL] DLQ send failed for key={}: {}", key, e.getMessage());
        }
    }
}
