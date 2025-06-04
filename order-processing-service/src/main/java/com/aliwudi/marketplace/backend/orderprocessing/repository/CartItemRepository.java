package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.common.model.CartItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CartItemRepository extends R2dbcRepository<CartItem, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<CartItem> findAllBy(Pageable pageable);

    // --- CartItem Specific Queries ---

    /**
     * Find all cart items belonging to a specific cart with pagination.
     */
    Flux<CartItem> findByCartId(Long cartId, Pageable pageable);

    /**
     * Find all cart items belonging to a specific cart without pagination.
     * We already expect a maximum allowable cart items in a cart
     */
    Flux<CartItem> findByCartId(Long cartId);//IMPORTANT

    /**
     * Find all cart items containing a specific product with pagination.
     */
    Flux<CartItem> findByProductId(Long productId, Pageable pageable);

    /**
     * Find a specific cart item by cart ID and product ID.
     */
    Mono<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    // --- Count Queries ---

    /**
     * Count all cart items.
     */
    Mono<Long> count();

    /**
     * Count all cart items for a specific cart.
     */
    Mono<Long> countByCartId(Long cartId);

    /**
     * Count all cart items for a specific product.
     */
    Mono<Long> countByProductId(Long productId);

    /**
     * Check if a specific product exists in a specific cart.
     */
    Mono<Boolean> existsByCartIdAndProductId(Long cartId, Long productId);

    /**
     * Delete all cart items for a given cart ID.
     */
    Mono<Void> deleteByCartId(Long cartId);
    
    /**
     * Delete cart item for a given userId and ProductId.
     */
    Mono<Void> deleteByUserIdAndProductId(Long userId, Long productId);


    /**
     * Remove a cart item by cart ID and product ID.
     */
    Mono<Void> deleteByCartIdAndProductId(Long cartId, Long productId);

    /**
     * Update the quantity of a cart item by cart ID and product ID.
     * The @Modifying annotation is required for UPDATE and DELETE queries.
     * The @Query annotation is used to define the custom query.
     */
    @Modifying
    @Query("UPDATE cart_item SET quantity = :quantity WHERE cart_id = :cartId AND product_id = :productId")
    Mono<Integer> updateQuantityByCartIdAndProductId(Long quantity, Long cartId, Long productId);    
}