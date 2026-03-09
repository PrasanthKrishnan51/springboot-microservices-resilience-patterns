package com.pk.ecommerce.common.util;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * Injects a correlation-id into MDC so every log line in a request
 * chain carries the same ID – essential for distributed tracing.
 */
public final class CorrelationIdUtil {
    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY     = "correlationId";

    private CorrelationIdUtil() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static void set(String id) {
        MDC.put(MDC_KEY, id);
    }

    public static String getOrGenerate() {
        String id = MDC.get(MDC_KEY);
        if (id == null || id.isBlank()) {
            id = generate();
            set(id);
        }
        return id;
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
