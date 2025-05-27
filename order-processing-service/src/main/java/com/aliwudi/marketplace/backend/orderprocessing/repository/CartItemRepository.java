// CartItemRepository.java
package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // Custom method to find a CartItem by Cart and Product
    // This is useful for checking if a product is already in the cart
    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);

    // Optional: Delete a CartItem by Cart and Product (if not using orphanRemoval via entity relationship)
    // int deleteByCartAndProduct(Cart cart, Product product);
}