// SellerRepository.java
package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Seller;
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // NEW: Import ReactiveCrudRepository
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux; // NEW: Import Flux for multiple results
import reactor.core.publisher.Mono; // NEW: Import Mono for single results or completion

// Remove old JpaRepository import, Page, and Pageable imports

@Repository
// NEW: Extend ReactiveCrudRepository instead of JpaRepository
public interface SellerRepository extends ReactiveCrudRepository<Seller, Long> {
    // ReactiveCrudRepository provides basic reactive CRUD operations.

    // Old: Optional<Seller> findByName(String name);
    // NEW: Returns Mono for zero or one result.
    Mono<Seller> findByName(String name);

    // Old: Page<Seller> findByNameContainingIgnoreCase(String name, Pageable pageable);
    // NEW: For pagination, use Flux with offset and limit parameters, which R2DBC translates to SQL OFFSET/LIMIT.
    Flux<Seller> findByNameContainingIgnoreCase(String name, Long offset, Integer limit);
    // NEW: Add a count method for total elements when paginating.
    Mono<Long> countByNameContainingIgnoreCase(String name);

    public Flux<Seller> findAll(Long offset, Integer limit);
}