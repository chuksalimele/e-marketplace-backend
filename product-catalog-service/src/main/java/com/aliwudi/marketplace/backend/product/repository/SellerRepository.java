// SellerRepository.java
package com.marketplace.emarketplacebackend.repository;

import com.marketplace.emarketplacebackend.model.Seller;
import org.springframework.data.domain.Page;    // NEW IMPORT
import org.springframework.data.domain.Pageable; // NEW IMPORT
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; 

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {
    // JpaRepository provides basic CRUD operations.

    // You can add custom methods here if needed, e.g.,
     Optional<Seller> findByName(String name);

    // NEW: Method for finding sellers by name with pagination and sorting
    Page<Seller> findByNameContainingIgnoreCase(String name, Pageable pageable);
}