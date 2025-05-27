package com.aliwudi.marketplace.backend.lgtmed.service;


import com.aliwudi.marketplace.backend.lgtmed.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.model.Delivery.DeliveryStatus;
import com.aliwudi.marketplace.backend.lgtmed.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    @Transactional
    public Delivery createDelivery(String orderId, String recipientName, String recipientAddress, String deliveryAgent, LocalDateTime estimatedDeliveryDate) {
        log.info("Creating delivery for orderId: {}", orderId);
        // In a real system, you might generate tracking number via a 3rd party logistics API
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .trackingNumber(trackingNumber)
                .status(DeliveryStatus.PENDING)
                .currentLocation("Order Confirmed")
                .recipientName(recipientName)
                .recipientAddress(recipientAddress)
                .deliveryAgent(deliveryAgent)
                .estimatedDeliveryDate(estimatedDeliveryDate != null ? estimatedDeliveryDate : LocalDateTime.now().plusDays(1)) // Default 1 day for 24-hr promise
                .build();
        return deliveryRepository.save(delivery);
    }

    @Transactional(readOnly = true)
    public Delivery getDeliveryByOrderId(String orderId) {
        log.info("Fetching delivery for orderId: {}", orderId);
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found for orderId: " + orderId));
    }

    @Transactional(readOnly = true)
    public Delivery getDeliveryByTrackingNumber(String trackingNumber) {
        log.info("Fetching delivery for trackingNumber: {}", trackingNumber);
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new RuntimeException("Delivery not found for trackingNumber: " + trackingNumber));
    }

    @Transactional
    public Delivery updateDeliveryStatus(String trackingNumber, DeliveryStatus newStatus, String currentLocation, String notes) {
        log.info("Updating status for trackingNumber: {} to {}", trackingNumber, newStatus);
        Delivery delivery = deliveryRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new RuntimeException("Delivery not found for trackingNumber: " + trackingNumber));

        delivery.setStatus(newStatus);
        if (currentLocation != null && !currentLocation.isEmpty()) {
            delivery.setCurrentLocation(currentLocation);
        }
        if (newStatus == DeliveryStatus.DELIVERED || newStatus == DeliveryStatus.FAILED || newStatus == DeliveryStatus.RETURNED) {
            delivery.setActualDeliveryDate(LocalDateTime.now());
        }
        // You might store notes in a separate audit log or notes field
        // delivery.setNotes(notes);
        return deliveryRepository.save(delivery);
    }
}