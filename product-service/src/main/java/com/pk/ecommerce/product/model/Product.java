package com.pk.ecommerce.product.model;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class Product {
    @Id
    private String id;
    @NonNull
    private String name;
    private String description;
    private String category;
    @NonNull
    private BigDecimal price;
    @NonNull
    private String sku;
    private boolean active;
    private LocalDateTime createdAt;
}
