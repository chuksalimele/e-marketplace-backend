// CartItemRepository.java
package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

@Repository
public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {

    // Custom method to find a CartItem by Cart and Product
    // This is useful for checking if a product is already in the cart
    Mono<CartItem> findByCartAndProductId(Cart cart, Long productId);

    // Optional: Delete a CartItem by Cart and Product (if not using orphanRemoval via entity relationship)
    //Mono<Integer> deleteByCartAndProductId(Cart cart, Long productId);

    public Mono<Boolean>  deleteByCart(Cart cart);
}