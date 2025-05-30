// ProductRepository.java
package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Product;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProductRepository extends ReactiveCrudRepository<Product, Long> {

    Mono<Product> findByName(String name);

    // --- Reactive Pagination Methods ---
    // These methods now accept 'offset' (for skipping records) and 'limit' (for taking records)
    // Spring Data R2DBC will translate these into SQL OFFSET and LIMIT clauses,
    // ensuring pagination happens at the database level.

    // Also adding count methods for proper pagination metadata (total elements)
    
    public Flux<Product> findAll(Long offset, Integer limit);

    Flux<Product> findByStore_Id(Long storeId, Long offset, Integer limit);
    Mono<Long> countByStore_Id(Long storeId);

    Flux<Product> findByStore_Seller_Id(Long sellerId, Long offset, Integer limit);
    Mono<Long> countByStore_Seller_Id(Long sellerId);

    Flux<Product> findByCategory_Name(String categoryName, Long offset, Integer limit);
    Mono<Long> countByCategory_Name(String categoryName);

    Flux<Product> findByCategory_NameAndStore_Location(String categoryName, String location, Long offset, Integer limit);
    Mono<Long> countByCategory_NameAndStore_Location(String categoryName, String location);

    Flux<Product> findByNameContainingIgnoreCase(String searchTerm, Long offset, Integer limit);
    Mono<Long> countByNameContainingIgnoreCase(String searchTerm);

    Flux<Product> findByStore_LocationIgnoreCase(String location, Long offset, Integer limit);
    Mono<Long> countByStore_LocationIgnoreCase(String location);

    Flux<Product> findByStore_IdAndStore_LocationIgnoreCase(Long storeId, String location, Long offset, Integer limit);
    Mono<Long> countByStore_IdAndStore_LocationIgnoreCase(Long storeId, String location);

    Flux<Product> findByNameContainingIgnoreCaseAndStore_LocationIgnoreCase(String productName, String location, Long offset, Integer limit);
    Mono<Long> countByNameContainingIgnoreCaseAndStore_LocationIgnoreCase(String productName, String location);

    // You can also add more advanced queries using @Query annotation if derived query methods become too complex,
    // ensuring to use R2DBC-compatible SQL with OFFSET/LIMIT.
    // Example:
    // @Query("SELECT * FROM product WHERE name LIKE :name OFFSET :offset LIMIT :limit")
    // Flux<Product> findProductsByNameWithPagination(@Param("name") String name, @Param("offset") Long offset, @Param("limit") Integer limit);


    public Mono<Long> countByLocation(String location);

    public Flux<Product>  findByStore_IdAndLocation(Long storeId, String location, Long offset, Integer limit);

    public Mono<Long> countByStore_IdAndLocation(Long storeId, String location);

    public Flux<Product> findByCategory_NameAndLocation(String categoryName, String location, Long offset, Integer limit);

    public Mono<Long> countByCategory_NameAndLocation(String categoryName, String location);

    public Flux<Product> findByNameContainingIgnoreCaseAndLocationContainingIgnoreCase(String productName, String location, Long offset, Integer limit);

    public Mono<Long> countByNameContainingIgnoreCaseAndLocationContainingIgnoreCase(String productName, String location);

   
}