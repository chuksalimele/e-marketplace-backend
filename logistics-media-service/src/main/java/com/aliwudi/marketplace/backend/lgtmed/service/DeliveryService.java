package com.aliwudi.marketplace.backend.lgtmed.service;

import com.aliwudi.marketplace.backend.lgtmed.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.model.Delivery.DeliveryStatus;
import com.aliwudi.marketplace.backend.lgtmed.repository.DeliveryRepository; // Assumed to be a Reactive Repository
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Keep for reactive transaction management if configured
import reactor.core.publisher.Mono; // NEW: Import Mono

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    @Transactional // Apply if you have a reactive transaction manager (e.g., for R2DBC)
    public Mono<Delivery> createDelivery(String orderId, String recipientName, String recipientAddress, String deliveryAgent, LocalDateTime estimatedDeliveryDate) {
        log.info("Creating delivery for orderId: {}", orderId);
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .trackingNumber(trackingNumber)
                .status(DeliveryStatus.PENDING)
                .currentLocation("Order Confirmed")
                .recipientName(recipientName)
                .recipientAddress(recipientAddress)
                .deliveryAgent(deliveryAgent)
                .estimatedDeliveryDate(estimatedDeliveryDate != null ? estimatedDeliveryDate : LocalDateTime.now().plusDays(1))
                .build();

        return deliveryRepository.save(delivery) // Returns Mono<Delivery>
                .doOnSuccess(savedDelivery -> log.info("Delivery created successfully with tracking number: {}", savedDelivery.getTrackingNumber()));
    }

    @Transactional(readOnly = true)
    public Mono<Delivery> getDeliveryByOrderId(String orderId) {
        log.info("Fetching delivery for orderId: {}", orderId);
        // Using switchIfEmpty to handle the case where the delivery is not found
        return deliveryRepository.findByOrderId(orderId) // Assumed to return Mono<Delivery>
                .switchIfEmpty(Mono.error(new RuntimeException("Delivery not found for orderId: " + orderId)))
                .doOnSuccess(delivery -> log.info("Found delivery for orderId: {}", orderId));
    }

    @Transactional(readOnly = true)
    public Mono<Delivery> getDeliveryByTrackingNumber(String trackingNumber) {
        log.info("Fetching delivery for trackingNumber: {}", trackingNumber);
        // Using switchIfEmpty to handle the case where the delivery is not found
        return deliveryRepository.findByTrackingNumber(trackingNumber) // Assumed to return Mono<Delivery>
                .switchIfEmpty(Mono.error(new RuntimeException("Delivery not found for trackingNumber: " + trackingNumber)))
                .doOnSuccess(delivery -> log.info("Found delivery for trackingNumber: {}", trackingNumber));
    }

    @Transactional
    public Mono<Delivery> updateDeliveryStatus(String trackingNumber, DeliveryStatus newStatus, String currentLocation, String notes) {
        log.info("Attempting to update status for trackingNumber: {} to {}", trackingNumber, newStatus);
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new RuntimeException("Delivery not found for trackingNumber: " + trackingNumber)))
                .flatMap(delivery -> {
                    log.info("Updating delivery details for trackingNumber: {}", trackingNumber);
                    delivery.setStatus(newStatus);
                    if (currentLocation != null && !currentLocation.isEmpty()) {
                        delivery.setCurrentLocation(currentLocation);
                    }
                    if (newStatus == DeliveryStatus.DELIVERED || newStatus == DeliveryStatus.FAILED || newStatus == DeliveryStatus.RETURNED) {
                        delivery.setActualDeliveryDate(LocalDateTime.now());
                    }
                    // You might store notes in a separate audit log or notes field
                    // delivery.setNotes(notes);
                    return deliveryRepository.save(delivery); // Returns Mono<Delivery>
                })
                .doOnSuccess(updatedDelivery -> log.info("Delivery status updated successfully for tracking number: {}", updatedDelivery.getTrackingNumber()));
    }
}