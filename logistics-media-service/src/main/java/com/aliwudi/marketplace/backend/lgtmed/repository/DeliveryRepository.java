package com.aliwudi.marketplace.backend.lgtmed.repository;

import com.aliwudi.marketplace.backend.lgtmed.model.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface DeliveryRepository extends ReactiveCrudRepository<Delivery, Long> {
    Mono<Delivery> findByOrderId(String orderId);
    Mono<Delivery> findByTrackingNumber(String trackingNumber);
}