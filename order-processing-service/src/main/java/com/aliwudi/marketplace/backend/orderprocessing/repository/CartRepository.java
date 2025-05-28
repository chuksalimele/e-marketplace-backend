// CartRepository.java
package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    // Custom method to find a Cart by the User it belongs to
    // Spring Data JPA can automatically generate the query for this method name
    Optional<Cart> findByUserId(Long userId);
}