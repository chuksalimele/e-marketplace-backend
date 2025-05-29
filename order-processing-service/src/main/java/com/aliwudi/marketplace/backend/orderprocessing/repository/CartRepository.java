// CartRepository.java
package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

@Repository
public interface CartRepository extends ReactiveCrudRepository<Cart, Long> {

    // Custom method to find a Cart by the User it belongs to
    // Spring Data JPA can automatically generate the query for this method name
    Mono<Cart> findByUserId(Long userId);
}