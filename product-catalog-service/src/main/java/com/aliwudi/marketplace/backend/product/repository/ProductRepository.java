// ProductRepository.java
package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Product;
import org.springframework.data.domain.Page; // NEW IMPORT
import org.springframework.data.domain.Pageable; // NEW IMPORT
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional; 

import java.util.List;

@Repository // Optional but good practice for clarity
public interface ProductRepository extends JpaRepository<Product, Long> {
    // JpaRepository provides methods like save(), findById(), findAll(), deleteById(), etc.

    // You can define custom query methods by following Spring Data JPA naming conventions:
    Optional<Product> findByName(String name);

    Page<Product> findByStore_Id(Long storeId, Pageable pageable);

    Page<Product> findByStore_Seller_Id(Long sellerId, Pageable pageable); // To get all products for a seller across all their stores

    Page<Product> findByCategory_Name(String categoryName, Pageable pageable);

    Page<Product> findByCategory_NameAndStore_Location(String categoryName, String location, Pageable pageable);
    
    Page<Product> findByNameContainingIgnoreCase(String searcTerm, Pageable pageable);
    
    // For location-based search, you might add:
    Page<Product> findByStore_LocationIgnoreCase(String location, Pageable pageable);
    
    Page<Product> findByStore_IdAndStore_LocationIgnoreCase(Long storeId, String location, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndStore_LocationIgnoreCase(String product_name, String location, Pageable pageable);

    // Or more advanced queries for geo-spatial searching once you implement that.    

}