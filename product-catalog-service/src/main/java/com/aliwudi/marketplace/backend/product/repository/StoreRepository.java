package com.marketplace.emarketplacebackend.repository;

import com.marketplace.emarketplacebackend.model.Seller;
import com.marketplace.emarketplacebackend.model.Store;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;    // NEW IMPORT
import org.springframework.data.domain.Pageable; // NEW IMPORT
import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store>  findByName(String name);
    Page<Store> findBySeller_Id(Long sellerId, Pageable pageable);
    Page<Store> findByLocationIgnoreCase(String locationPart, Pageable pageable); // For simple location search
    // You might need more complex queries for geographical proximity later
}