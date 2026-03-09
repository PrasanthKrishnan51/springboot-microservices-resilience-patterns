package com.pk.ecommerce.inventory.repository;


import com.pk.ecommerce.inventory.model.Inventory;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryRepository extends MongoRepository<Inventory, String> {
}
