package com.aliwudi.marketplace.backend.product.repository;

import com.aliwudi.marketplace.backend.product.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; // Add this import

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name); // Add this method
}