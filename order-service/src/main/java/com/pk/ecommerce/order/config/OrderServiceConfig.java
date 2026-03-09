package com.pk.ecommerce.order.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
public class OrderServiceConfig {

    @Value("${services.inventory-service.url}") private String inventoryUrl;
    @Value("${services.payment-service.url}")   private String paymentUrl;
    @Value("${services.product-service.url}")   private String productUrl;

    // ── WebClients ────────────────────────────────────────────────
    @Bean("inventoryClient")
    public WebClient inventoryClient() {
        return buildClient(inventoryUrl, "inventory-service");
    }

    @Bean("paymentClient")
    public WebClient paymentClient() {
        return buildClient(paymentUrl, "payment-service");
    }

    @Bean("productClient")
    public WebClient productClient() {
        return buildClient(productUrl, "product-service");
    }

    private WebClient buildClient(String baseUrl, String serviceName) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(logRequest(serviceName))
                .filter(logResponse(serviceName))
                .filter(errorMapper())
                .build();
    }

    // ── Kafka Topics (auto-create with correct partitions) ────────
    @Bean NewTopic orderCreated()    { return topic("ecom.order.created",    6); }
    @Bean NewTopic orderConfirmed()  { return topic("ecom.order.confirmed",  6); }
    @Bean NewTopic orderCancelled()  { return topic("ecom.order.cancelled",  3); }
    @Bean NewTopic orderDlq()        { return retentionTopic("ecom.order.dlq", 1, "2592000000"); /* 30 days */ }
    @Bean NewTopic paymentRequested(){ return topic("ecom.payment.requested", 6); }
    @Bean NewTopic inventoryReserve(){ return topic("ecom.inventory.reserve", 6); }

    private NewTopic topic(String name, int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
    private NewTopic retentionTopic(String name, int partitions, String retentionMs) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1)
                .config("retention.ms", retentionMs).build();
    }

    // ── Kafka Template ────────────────────────────────────────────
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        var t = new KafkaTemplate<String, Object>(pf);
        t.setObservationEnabled(true);
        return t;
    }

    // ── Async thread pool (used by @TimeLimiter + CompletableFuture) ──
    @Bean("orderExecutor")
    public Executor orderExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(10);
        ex.setMaxPoolSize(20);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("order-async-");
        ex.setRejectedExecutionHandler((r, e) ->
                log.error("Thread pool saturated – order async task rejected"));
        ex.initialize();
        return ex;
    }

    // ── WebClient filters ─────────────────────────────────────────
    private ExchangeFilterFunction logRequest(String svc) {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
            log.debug("[{}→] {} {}", svc, req.method(), req.url());
            return Mono.just(req);
        });
    }

    private ExchangeFilterFunction logResponse(String svc) {
        return ExchangeFilterFunction.ofResponseProcessor(res -> {
            log.debug("[{}←] status={}", svc, res.statusCode());
            return Mono.just(res);
        });
    }

    private ExchangeFilterFunction errorMapper() {
        return ExchangeFilterFunction.ofResponseProcessor(res -> {
            if (res.statusCode().is5xxServerError()) {
                return res.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(
                                new RuntimeException("Downstream 5xx: " + body)));
            }
            return Mono.just(res);
        });
    }
}
