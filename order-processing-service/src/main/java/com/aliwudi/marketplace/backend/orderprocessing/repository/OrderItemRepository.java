// src/main/java/com/marketplace/emarketplacebackend/repository/OrderItemRepository.java
package com.aliwudi.marketplace.backend.orderprocessing.repository;

import com.aliwudi.marketplace.backend.orderprocessing.model.Order;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface OrderItemRepository extends ReactiveCrudRepository<OrderItem, Long> {
    Mono<Void> deleteByOrder(Order order);
}