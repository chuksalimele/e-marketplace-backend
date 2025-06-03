package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.common.model.Order;
import com.aliwudi.marketplace.backend.common.status.OrderStatus; // Assuming this enum is defined
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends R2dbcRepository<Order, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<Order> findAllBy(Pageable pageable);

    // --- Order Filtering and Search with Pagination ---

    /**
     * Find orders placed by a specific user with pagination.
     */
    Flux<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * Find orders by their current status with pagination.
     */
    Flux<Order> findByOrderStatus(OrderStatus orderStatus, Pageable pageable);

    /**
     * Find orders placed within a specific time range with pagination.
     */
    Flux<Order> findByOrderTimeBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * Find orders by a specific user and status with pagination.
     */
    Flux<Order> findByUserIdAndOrderStatus(Long userId, OrderStatus orderStatus, Pageable pageable);

    // --- Count Queries ---

    /**
     * Count all orders.
     */
    Mono<Long> count();

    /**
     * Count orders placed by a specific user.
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * Count orders by their current status.
     */
    Mono<Long> countByOrderStatus(OrderStatus orderStatus);

    /**
     * Count orders placed within a specific time range.
     */
    Mono<Long> countByOrderTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Count orders by a specific user and status.
     */
    Mono<Long> countByUserIdAndOrderStatus(Long userId, OrderStatus orderStatus);

    public Mono<Object> deleteAll(List<Object> orders);
}