package com.pk.ecommerce.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    @Id
    private String productId;
    @Column(nullable = false)
    private Integer totalStock;
    @Column(nullable = false)
    private Integer reservedStock;
    private LocalDateTime updatedAt;

    public Integer getAvailableStock() {
        return totalStock - reservedStock;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
