# 🛒 E-Commerce Platform
## springboot-resilience-patterns

---

## 🏗️ Architecture

```
                     ┌─────────────────────────────────────────────┐
  Client ───────────▶│          API GATEWAY  :8080                 │
                     │  Spring Cloud Gateway                       │
                     │  • Redis Rate Limiter (per IP / per user)   │
                     │  • Circuit Breaker per route                │
                     │  • Retry with backoff                       │
                     │  • Correlation-ID injection                 │
                     │  • Fallback endpoints (/fallback/*)         │
                     └────┬────────┬─────────┬───────────┬─────────┘
                          │        │         │           │
              ┌───────────▼─┐  ┌───▼──┐  ┌───▼───┐  ┌────▼────┐
              │ ORDER SVC   │  │PROD  │  │ INV   │  │ PAY     │
              │  :8081      │  │:8082 │  │ :8083 │  │ :8084   │
              │             │  │      │  │       │  │         │
              │ @RateLimiter│  │@RL   │  │@CB    │  │@CB      │
              │ @CB         │  │@CB   │  │@Retry │  │@Retry   │
              │ @Retry      │  │      │  │@BH    │  │@BH      │
              │ @Bulkhead   │  │      │  │@RL    │  │@RL      │
              │ @TimeLimiter│  │      │  │       │  │@TL      │
              │ @Fallback   │  │      │  │       │  │@Fallback│
              └──────┬──────┘  └──────┘  └───────┘  └─────────┘
                     │
       ┌─────────────▼──────────────────────────────────────────┐
       │                    KAFKA                               │
       │  ecom.order.*  ecom.payment.*  ecom.inventory.*        │
       │  + Dead Letter Queues (*.dlq)                          │
       │  + @RetryableTopic (auto retry topics)                 │
       └──────────────────────────┬─────────────────────────────┘
                                  │
                       ┌──────────▼──────────┐
                       │  NOTIFICATION SVC   │
                       │       :8085         │
                       │  Email│SMS│Push     │
                       └─────────────────────┘
```

---

## 🛡️ Resilience4j Pattern Guide

### 1. Circuit Breaker
**What it does:** Monitors calls to a dependency. If failures exceed a threshold, it "trips" to OPEN state and rejects all calls immediately (fail-fast). After a wait period it enters HALF_OPEN to probe recovery.

```
CLOSED ──(failures > 50%)──▶ OPEN ──(30s wait)──▶ HALF_OPEN ──(probe OK)──▶ CLOSED
                                                              └──(probe FAIL)──▶ OPEN
```

**Config:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryServiceCB:
        slidingWindowSize: 10          # evaluate last 10 calls
        failureRateThreshold: 50       # trip at 50% failure
        waitDurationInOpenState: 30s   # stay open 30s
        minimumNumberOfCalls: 5        # need 5 calls before tripping
```

**Annotation:**
```java
@CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "inventoryFallback")
public CompletableFuture<ApiResponse<String>> checkInventory(...) { ... }
```

---

### 2. Retry
**What it does:** Automatically retries failed calls with configurable backoff. Critical: must be idempotent for POST/payment calls.

```yaml
resilience4j:
  retry:
    instances:
      inventoryServiceRetry:
        maxAttempts: 3                 # 1 original + 2 retries
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2  # 500ms → 1s → 2s
```

---

### 3. Bulkhead (Semaphore)
**What it does:** Limits concurrent calls to a service. Prevents one slow dependency from consuming all threads and starving other operations.

```yaml
resilience4j:
  bulkhead:
    instances:
      paymentServiceBulkhead:
        maxConcurrentCalls: 10         # max 10 simultaneous payment calls
        maxWaitDuration: 1s            # wait up to 1s for a slot; then reject
```

---

### 4. Rate Limiter
**What it does:** Caps how many calls are made *per time window*. Two uses:
- **Gateway (inbound):** limits requests from clients (Redis token bucket)
- **Service (outbound):** limits calls TO a downstream service (e.g. payment gateway TPS limit)

```yaml
resilience4j:
  ratelimiter:
    instances:
      paymentGatewayLimiter:
        limitForPeriod: 20           # max 20 outbound calls per second
        limitRefreshPeriod: 1s
        timeoutDuration: 500ms       # wait 500ms for a permit before failing
```

---

### 5. Time Limiter
**What it does:** Cancels async operations that exceed a deadline. Works with `CompletableFuture`.

```yaml
resilience4j:
  timelimiter:
    instances:
      paymentGatewayTimeout:
        timeoutDuration: 15s
        cancelRunningFuture: true    # cancel the thread, don't just observe
```

---

### 6. Fallback
**What it does:** Every resilience annotation accepts `fallbackMethod`. Called when the circuit is open, bulkhead is full, rate limit is hit, all retries exhausted, or timeout occurred.

```java
// Original method
@CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "inventoryFallback")
public CompletableFuture<ApiResponse<String>> checkInventory(String orderId, ...) { ... }

// Fallback – MUST have same params + Throwable
public CompletableFuture<ApiResponse<String>> inventoryFallback(
        String orderId, ..., Throwable ex) {
    // Queue via Kafka, return cached data, return degraded response
    log.warn("[Fallback] Inventory unavailable: {}", ex.getClass().getSimpleName());
    return CompletableFuture.completedFuture(ApiResponse.fallback("..."));
}
```

---

### Decorator Order (matters!)
```
Bulkhead → CircuitBreaker → RateLimiter → Retry → TimeLimiter → actual call
```
Applied **outermost → innermost**. Bulkhead gates first (no point checking CB if saturated).

---

## 📨 Kafka Event Flow

```
POST /api/v1/orders
       │
       ▼
[ORDER_CREATED] ──┬──▶ Inventory Service ──▶ [INVENTORY_RESERVED / FAILED]
                  │                                    │
                  │              ┌─────────────────────▼──────────────────┐
                  │              │ RESERVED → [PAYMENT_REQUESTED]          │
                  │              │ FAILED   → [ORDER_CANCELLED]            │
                  │              └────────────────────────────────────────┘
                  │
                  └──▶ Notification Service (order confirmation email)
                              │
                  [PAYMENT_COMPLETED / FAILED]
                              │
                  Notification Service (payment receipt / failure alert)
```

### Topic Inventory

| Topic | Partitions | Purpose |
|-------|-----------|---------|
| `ecom.order.created` | 6 | New order events |
| `ecom.order.confirmed` | 6 | Order confirmed after payment |
| `ecom.order.cancelled` | 3 | Cancellations |
| `ecom.order.dlq` | 1 | Dead letters (30-day retention) |
| `ecom.payment.requested` | 6 | Payment initiation |
| `ecom.payment.completed` | 6 | Successful payments |
| `ecom.payment.failed` | 3 | Failed payments |
| `ecom.inventory.reserve` | 6 | Reserve requests |
| `ecom.inventory.reserved` | 6 | Reserve results |
| `ecom.notification.*` | 3 | Notification delivery |

---

## 🚀 Quick Start

### Option 1 – Docker Compose (recommended)
```bash
# Build all services
mvn clean package -DskipTests

# Start everything
docker-compose up -d

# Check health
curl http://localhost:8080/actuator/health
```

### Option 2 – Local (dev mode)
```bash
# Start infrastructure only
docker-compose up -d zookeeper kafka kafka-ui redis prometheus grafana

# Start each service (separate terminals)
cd order-service   && mvn spring-boot:run
cd product-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd api-gateway     && mvn spring-boot:run   # start last
```

---

## 📡 Service Ports

| Service | Port | Notes |
|---------|------|-------|
| API Gateway | 8080 | Single entry point |
| Order Service | 8081 | Core orchestrator |
| Product Service | 8082 | Product catalog |
| Inventory Service | 8083 | Stock management |
| Payment Service | 8084 | Payment gateway |
| Notification Service | 8085 | Email/SMS/Push |
| Kafka UI | 9090 | Topic browser |
| Prometheus | 9091 | Metrics |
| Grafana | 3000 | Dashboards (admin/admin123) |

---

## 📋 API Reference

### Place an Order
```http
POST http://localhost:8080/api/v1/orders
Content-Type: application/json

{
  "userId": "user-001",
  "shippingAddress": "123 Main St, Mumbai",
  "currency": "INR",
  "paymentMethod": "CARD",
  "items": [
    { "productId": "p-001", "productName": "iPhone 15", "quantity": 1, "unitPrice": 79999.00 }
  ]
}
```

### Live Resilience Dashboard
```http
GET http://localhost:8081/api/v1/orders/resilience/status
```
Returns real-time state of all circuit breakers, bulkheads, rate limiters, and retries.

---

## 📊 Key Metrics (Prometheus)

| Metric | What it tells you |
|--------|-------------------|
| `resilience4j_circuitbreaker_state` | 0=CLOSED, 1=OPEN, 2=HALF_OPEN |
| `resilience4j_circuitbreaker_failure_rate` | % failures; alert if > 40% |
| `resilience4j_ratelimiter_available_permissions` | Rate limiter headroom |
| `resilience4j_bulkhead_available_concurrent_calls` | Concurrency headroom |
| `resilience4j_retry_calls_total` | Retry frequency per service |
| `kafka_consumer_fetch_manager_records_lag` | Consumer lag per partition |
