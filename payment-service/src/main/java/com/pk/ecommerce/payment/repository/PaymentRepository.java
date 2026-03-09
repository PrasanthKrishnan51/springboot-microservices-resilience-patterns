package com.pk.ecommerce.payment.repository;

import com.pk.ecommerce.payment.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {
    java.util.Optional<Payment> findByOrderId(String orderId);
}
