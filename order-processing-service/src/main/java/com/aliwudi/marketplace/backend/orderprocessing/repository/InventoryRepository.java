package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.orderprocessing.model.Inventory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying; // For update operations
import org.springframework.data.r2dbc.repository.Query; // For custom queries
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface InventoryRepository extends R2dbcRepository<Inventory, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<Inventory> findAllBy(Pageable pageable);

    // --- Inventory Specific Queries ---

    /**
     * Find inventory by product ID. Assumes a one-to-one relationship (one inventory record per product).
     *
     * IMPORTANT: Assumes 'productId' in Inventory model is Long, not String.
     * Please update Inventory.java to 'private Long productId;'
     */
    Mono<Inventory> findByProductId(Long productId);

    /**
     * Find products with available quantity greater than a threshold, with pagination.
     */
    Flux<Inventory> findByAvailableQuantityGreaterThan(Integer quantity, Pageable pageable);

    // --- Update Queries (using @Modifying and @Query for direct updates) ---

    /**
     * Decrement the available quantity for a product.
     */
    @Modifying
    @Query("UPDATE inventory SET available_quantity = available_quantity - :quantity WHERE product_id = :productId AND available_quantity >= :quantity")
    Mono<Integer> decrementAvailableQuantity(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Increment the available quantity for a product.
     */
    @Modifying
    @Query("UPDATE inventory SET available_quantity = available_quantity + :quantity WHERE product_id = :productId")
    Mono<Integer> incrementAvailableQuantity(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * Update reserved quantity for a product.
     */
    @Modifying
    @Query("UPDATE inventory SET reserved_quantity = :reservedQuantity WHERE product_id = :productId")
    Mono<Integer> updateReservedQuantity(@Param("productId") Long productId, @Param("reservedQuantity") Integer reservedQuantity);

    // --- Count Queries ---

    /**
     * Count all inventory records.
     */
    Mono<Long> count();

    /**
     * Count products with available quantity greater than a threshold.
     */
    Mono<Long> countByAvailableQuantityGreaterThan(Integer quantity);

    /**
     * Check if an inventory record exists for a given product ID.
     */
    Mono<Boolean> existsByProductId(Long productId);
}