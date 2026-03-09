package com.pk.ecommerce.notification.event;

import com.pk.ecommerce.common.constants.KafkaTopics;
import com.pk.ecommerce.common.event.OrderCreatedEvent;
import com.pk.ecommerce.common.event.OrderStatusChangedEvent;
import com.pk.ecommerce.common.event.PaymentResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class NotificationEventConsumer {

    @RetryableTopic(attempts = "2", backoff = @Backoff(delay = 1000), dltTopicSuffix = ".dlq")
    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "notification-service-group")
    public void onOrderCreated(@Payload OrderCreatedEvent event,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("[NOTIFY] 📦 Order CREATED orderId={} userId={} amount={}",
                event.getOrderId(), event.getUserId(), event.getTotalAmount());
        send(event.getUserId(), "EMAIL",
                "Order Confirmed ✅",
                "Your order #" + event.getOrderId() + " for ₹" + event.getTotalAmount() + " has been placed.");
    }

    @RetryableTopic(attempts = "2", backoff = @Backoff(delay = 1000), dltTopicSuffix = ".dlq")
    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "notification-service-group")
    public void onPaymentCompleted(@Payload PaymentResultEvent event) {
        log.info("[NOTIFY] 💳 Payment COMPLETED orderId={} txn={}", event.getOrderId(), event.getTransactionId());
        send(event.getUserId(), "SMS",
                "Payment Successful",
                "Payment of ₹" + event.getAmount() + " for order #" + event.getOrderId() + " received.");
    }

    @RetryableTopic(attempts = "2", backoff = @Backoff(delay = 1000), dltTopicSuffix = ".dlq")
    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service-group")
    public void onPaymentFailed(@Payload PaymentResultEvent event) {
        log.warn("[NOTIFY] ❌ Payment FAILED orderId={} reason={}", event.getOrderId(), event.getFailureReason());
        send(event.getUserId(), "EMAIL",
                "Payment Failed ❌",
                "Payment for order #" + event.getOrderId() + " failed: " + event.getFailureReason());
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "notification-service-group")
    public void onOrderCancelled(@Payload OrderStatusChangedEvent event) {
        log.info("[NOTIFY] 🚫 Order CANCELLED orderId={}", event.getOrderId());
        send(event.getUserId(), "EMAIL",
                "Order Cancelled",
                "Your order #" + event.getOrderId() + " has been cancelled. Reason: " + event.getReason());
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_LOW_STOCK, groupId = "notification-service-group")
    public void onLowStock(@Payload Object event) {
        log.warn("[NOTIFY] ⚠️ LOW STOCK ALERT – {}", event);
        // Alert operations team via PagerDuty / Slack in production
    }

    private void send(String userId, String channel, String subject, String body) {
        // Production: route to SES (email), Twilio (SMS), FCM (push)
        log.info("[NOTIFY→] channel={} userId={} subject='{}'", channel, userId, subject);
    }
}
