package com.pk.ecommerce.gateway.filter;

import com.pk.ecommerce.common.util.CorrelationIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Runs before every route.
 * 1. Injects X-Correlation-Id if absent.
 * 2. Logs incoming request.
 * 3. Logs response status + duration.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(CorrelationIdUtil.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String cid = correlationId;
        final long t0 = System.currentTimeMillis();

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(CorrelationIdUtil.HEADER_NAME, cid)
                .build();

        log.info("[GW→] {} {} correlationId={} from={}",
                mutated.getMethod(), mutated.getURI().getPath(), cid,
                exchange.getRequest().getRemoteAddress());

        return chain.filter(exchange.mutate().request(mutated).build())
                .then(Mono.fromRunnable(() ->
                        log.info("[GW←] {} {} status={} duration={}ms correlationId={}",
                                mutated.getMethod(), mutated.getURI().getPath(),
                                exchange.getResponse().getStatusCode(),
                                System.currentTimeMillis() - t0, cid)
                ));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
