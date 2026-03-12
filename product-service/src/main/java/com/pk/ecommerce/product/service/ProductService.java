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

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository repo;
    private final KafkaTemplate<String, Object> kafka;


    @RateLimiter(name = "productReadLimiter", fallbackMethod = "allProductsRateLimitFallback")
    @CircuitBreaker(name = "externalPricingCB", fallbackMethod = "allProductsFallback")
    public ApiResponse<List<Product>> getAllProducts() {
        return ApiResponse.ok(repo.findByActiveTrue());
    }

    //@RateLimiter(name = "productReadLimiter", fallbackMethod = "productRateLimitFallback")
    @CircuitBreaker(name = "externalPricingCB", fallbackMethod = "pricingFallback")
    public ApiResponse<Product> getProduct(String id) {

        /*if(true){
            throw new RuntimeException("Service Unavailable");
        }*/

        return repo.findById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("Product not found", "PRODUCT_NOT_FOUND"));
    }

    public ApiResponse<Product> createProduct(CreateProductRequest req) {

        Product p = Product.builder()
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .price(req.getPrice())
                .sku(req.getSku())
                .build();

        p = repo.save(p);

        kafka.send(KafkaTopics.PRODUCT_CREATED, p.getId(), p);

        log.info("Product created id={} name={}", p.getId(), p.getName());

        return ApiResponse.ok(p, "Product created");
    }

    // CircuitBreaker fallback

    public ApiResponse<Product> pricingFallback(String id, Throwable ex) {

        log.warn("[CircuitBreaker] External pricing unavailable for productId={}", id);

        return ApiResponse.error(
                "Product catalog temporarily unavailable",
                "PRODUCT_SERVICE_DOWN"
        );
    }

    public ApiResponse<List<Product>> allProductsFallback(Throwable ex) {

        log.warn("[CircuitBreaker] Pricing service unavailable");

        return ApiResponse.error(
                "Product catalog temporarily unavailable",
                "PRODUCT_SERVICE_DOWN"
        );
    }

    // RateLimiter fallbacks

    public ApiResponse<List<Product>> allProductsRateLimitFallback(Throwable ex) {

        log.warn("[RateLimiter] Too many product requests");

        return ApiResponse.error(
                "Too many requests. Please retry later.",
                "PRODUCT_RATE_LIMITED"
        );
    }

    public ApiResponse<Product> productRateLimitFallback(String id, Throwable ex) {

        log.warn("[RateLimiter] Too many requests for product {}", id);

        return ApiResponse.error(
                "Too many requests. Please retry later.",
                "PRODUCT_RATE_LIMITED"
        );
    }
}
