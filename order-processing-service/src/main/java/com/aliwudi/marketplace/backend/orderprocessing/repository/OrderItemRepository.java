package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.common.model.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface OrderItemRepository extends R2dbcRepository<OrderItem, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<OrderItem> findAllBy(Pageable pageable);

    // --- OrderItem Specific Queries ---

    /**
     * Find all order items belonging to a specific order with pagination.
     */
    Flux<OrderItem> findByOrderId(Long orderId, Pageable pageable);

    /**
     * Find all order items containing a specific product with pagination.
     */
    Flux<OrderItem> findByProductId(Long productId, Pageable pageable);

    /**
     * Find a specific order item by order ID and product ID.
     */
    Mono<OrderItem> findByOrderIdAndProductId(Long orderId, Long productId);

    // --- Count Queries ---

    /**
     * Count all order items.
     */
    Mono<Long> count();

    /**
     * Count all order items for a specific order.
     */
    Mono<Long> countByOrderId(Long orderId);

    /**
     * Count all order items for a specific product.
     */
    Mono<Long> countByProductId(Long productId);

    /**
     * Check if a specific product exists within a specific order.
     */
    Mono<Boolean> existsByOrderIdAndProductId(Long orderId, Long productId);

    Mono<Boolean>  deleteByOrderId(Long orderId);
}