package com.pk.ecommerce.payment.event;

import com.pk.ecommerce.common.constants.KafkaTopics;
import com.pk.ecommerce.common.event.PaymentRequestedEvent;
import com.pk.ecommerce.payment.service.PaymentService;
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
public class PaymentEventConsumer {
    private final PaymentService paymentService;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 2000, multiplier = 2.0), dltTopicSuffix = ".dlq")
    @KafkaListener(topics = KafkaTopics.PAYMENT_REQUESTED, groupId = "payment-service-group")
    public void onPaymentRequested(
            @Payload PaymentRequestedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("[Kafka←] PaymentRequested orderId={} amount={}", event.getOrderId(), event.getAmount());
        paymentService.processPayment(
                event.getOrderId(), event.getAmount(), event.getCurrency(), event.getPaymentMethod());
    }
}
