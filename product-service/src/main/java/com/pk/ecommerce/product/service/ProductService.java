package com.pk.ecommerce.product.service;

import com.pk.ecommerce.common.constants.KafkaTopics;
import com.pk.ecommerce.common.dto.ApiResponse;
import com.pk.ecommerce.product.dto.CreateProductRequest;
import com.pk.ecommerce.product.model.Product;
import com.pk.ecommerce.product.repository.ProductRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repo;
    private final KafkaTemplate<String, Object> kafka;

    @RateLimiter(name = "productReadLimiter", fallbackMethod = "readFallback")
    public ApiResponse<List<Product>> getAllProducts() {
        return ApiResponse.ok(repo.findByActiveTrue());
    }

    @RateLimiter(name = "productReadLimiter", fallbackMethod = "readFallback")
    @CircuitBreaker(name = "externalPricingCB", fallbackMethod = "pricingFallback")
    public ApiResponse<Product> getProduct(String id) {
        return repo.findById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("Product not found", "PRODUCT_NOT_FOUND"));
    }

    public ApiResponse<Product> createProduct(CreateProductRequest req) {
        Product p = Product.builder()
                .name(req.getName()).description(req.getDescription())
                .category(req.getCategory()).price(req.getPrice()).sku(req.getSku())
                .build();
        p = repo.save(p);
        kafka.send(KafkaTopics.PRODUCT_CREATED, p.getId(), p);
        log.info("Product created id={} name={}", p.getId(), p.getName());
        return ApiResponse.ok(p, "Product created");
    }

    // Fallbacks
    public ApiResponse<List<Product>> readFallback(Throwable ex) {
        log.warn("[RateLimiter] Product read rate limited: {}", ex.getMessage());
        return ApiResponse.error("Product catalog temporarily rate limited. Please retry.", "PRODUCT_RATE_LIMITED");
    }

    public ApiResponse<Product> pricingFallback(String id, Throwable ex) {
        log.warn("[CircuitBreaker] External pricing unavailable for productId={}", id);
        return repo.findById(id)
                .map(p -> ApiResponse.ok(p, "Using cached price (live pricing unavailable)"))
                .orElse(ApiResponse.error("Product not found", "PRODUCT_NOT_FOUND"));
    }
}
