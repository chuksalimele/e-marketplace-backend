package com.aliwudi.marketplace.backend.lgtmed.repository;

import com.aliwudi.marketplace.backend.lgtmed.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.model.Delivery.DeliveryStatus; // Import if not already
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux; // NEW: Import Flux
import reactor.core.publisher.Mono;

public interface DeliveryRepository extends ReactiveCrudRepository<Delivery, Long> {
    Mono<Delivery> findByOrderId(String orderId);
    Mono<Delivery> findByTrackingNumber(String trackingNumber);

    // NEW: For getDeliveriesByAgent
    Flux<Delivery> findByDeliveryAgent(String deliveryAgent);
    Flux<Delivery> findByDeliveryAgentAndStatus(String deliveryAgent, DeliveryStatus status); // Example for filtering
    Mono<Long> countByDeliveryAgent(String deliveryAgent);

    // NEW: For searchDeliveries
    Flux<Delivery> findByRecipientNameContainingIgnoreCaseOrRecipientAddressContainingIgnoreCaseOrDeliveryAgentContainingIgnoreCase(
            String recipientName, String recipientAddress, String deliveryAgent);

    Mono<Long> countByRecipientNameContainingIgnoreCaseOrRecipientAddressContainingIgnoreCaseOrDeliveryAgentContainingIgnoreCase(
            String recipientName, String recipientAddress, String deliveryAgent);
}