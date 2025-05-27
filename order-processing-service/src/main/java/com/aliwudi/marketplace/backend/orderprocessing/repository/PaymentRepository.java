package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.ecommerce.orderprocessing.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findByTransactionRef(String transactionRef);
}