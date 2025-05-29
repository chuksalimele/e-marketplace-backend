package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Store;
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // NEW: Import ReactiveCrudRepository
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux; // NEW: Import Flux for multiple results
import reactor.core.publisher.Mono; // NEW: Import Mono for single results or completion

// Remove old JpaRepository import, Page, and Pageable imports

@Repository
// NEW: Extend ReactiveCrudRepository instead of JpaRepository
public interface StoreRepository extends ReactiveCrudRepository<Store, Long> {

    // Old: Optional<Store> findByName(String name);
    // NEW: Returns Mono for zero or one result.
    Mono<Store> findByName(String name);

    // Old: Page<Store> findBySeller_Id(Long sellerId, Pageable pageable);
    // NEW: For pagination, use Flux with offset and limit parameters, which R2DBC translates to SQL OFFSET/LIMIT.
    Flux<Store> findBySeller_Id(Long sellerId, Long offset, Integer limit);
    // NEW: Add a count method for total elements when paginating.
    Mono<Long> countBySeller_Id(Long sellerId);

    // Old: Page<Store> findByLocationIgnoreCase(String locationPart, Pageable pageable);
    Flux<Store> findByLocationIgnoreCase(String locationPart, Long offset, Integer limit);
    Mono<Long> countByLocationIgnoreCase(String locationPart);

    // You might need more complex queries for geographical proximity later,
    // which would also return Flux/Mono.
}