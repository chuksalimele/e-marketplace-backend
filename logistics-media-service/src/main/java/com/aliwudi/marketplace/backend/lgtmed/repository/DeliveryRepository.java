package com.aliwudi.marketplace.backend.lgtmed.repository;

import com.aliwudi.marketplace.backend.common.status.DeliveryStatus;
import com.aliwudi.marketplace.backend.lgtmed.model.Delivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface DeliveryRepository extends R2dbcRepository<Delivery, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<Delivery> findAllBy(Pageable pageable);

    // --- Delivery Specific Queries ---
    Mono<Delivery> findByOrderId(Long orderId); // Assumes one delivery record per order
    Mono<Delivery> findByTrackingNumber(String trackingNumber); // Tracking number is unique

    Flux<Delivery> findByStatus(DeliveryStatus status, Pageable pageable);
    Flux<Delivery> findByDeliveryAgent(String deliveryAgent, Pageable pageable);
    Flux<Delivery> findByEstimatedDeliveryDateBefore(LocalDateTime date, Pageable pageable);
    Flux<Delivery> findByCurrentLocationContainingIgnoreCase(String location, Pageable pageable);

    // --- Count Queries ---
    Mono<Long> count();
    Mono<Long> countByOrderId(Long orderId);
    Mono<Long> countByStatus(DeliveryStatus status);
    Mono<Long> countByDeliveryAgent(String deliveryAgent);
    Mono<Long> countByEstimatedDeliveryDateBefore(LocalDateTime date);
    Mono<Long> countByCurrentLocationContainingIgnoreCase(String location);
    Mono<Boolean> existsByTrackingNumber(String trackingNumber);
}