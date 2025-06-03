package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.common.model.Payment;
import com.aliwudi.marketplace.backend.common.status.PaymentStatus; // Assuming this enum is defined
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface PaymentRepository extends R2dbcRepository<Payment, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<Payment> findAllBy(Pageable pageable);

    // --- Payment Specific Queries ---

    /**
     * Find a payment by its associated order ID. Assumes one payment per order.
     *
     * IMPORTANT: Assumes 'orderId' in Payment model is Long, not String.
     * Please update Payment.java to 'private Long orderId;'
     */
    Mono<Payment> findByOrderId(Long orderId);

    /**
     * Find payments made by a specific user with pagination.
     *
     * IMPORTANT: Assumes 'userId' in Payment model is Long, not String.
     * Please update Payment.java to 'private Long userId;'
     */
    Flux<Payment> findByUserId(Long userId, Pageable pageable);

    /**
     * Find payments by their status with pagination.
     */
    Flux<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    /**
     * Find payments made within a specific time range with pagination.
     */
    Flux<Payment> findByPaymentTimeBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * Find a payment by its unique transaction reference.
     */
    Mono<Payment> findByTransactionRef(String transactionRef);

    // --- Count Queries ---

    /**
     * Count all payments.
     */
    Mono<Long> count();

    /**
     * Count payments made by a specific user.
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * Count payments by their status.
     */
    Mono<Long> countByStatus(PaymentStatus status);

    /**
     * Count payments made within a specific time range.
     */
    Mono<Long> countByPaymentTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Check if a payment with a given transaction reference exists.
     */
    Mono<Boolean> existsByTransactionRef(String transactionRef);
}