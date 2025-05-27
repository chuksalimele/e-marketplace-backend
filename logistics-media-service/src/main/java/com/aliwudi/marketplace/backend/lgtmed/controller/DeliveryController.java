package com.aliwudi.marketplace.backend.lgtmed.controller;


import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryRequest;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryResponse;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryUpdateRequest;
import com.aliwudi.marketplace.backend.lgtmed.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping
    public ResponseEntity<DeliveryResponse> createDelivery(@RequestBody DeliveryRequest request) {
        Delivery delivery = deliveryService.createDelivery(
                request.getOrderId(),
                request.getRecipientName(),
                request.getRecipientAddress(),
                request.getDeliveryAgent(),
                request.getEstimatedDeliveryDate()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(delivery, "Delivery created successfully"));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResponse> getDeliveryByOrderId(@PathVariable String orderId) {
        Delivery delivery = deliveryService.getDeliveryByOrderId(orderId);
        return ResponseEntity.ok(convertToDto(delivery, "Delivery found"));
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<DeliveryResponse> getDeliveryByTrackingNumber(@PathVariable String trackingNumber) {
        Delivery delivery = deliveryService.getDeliveryByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(convertToDto(delivery, "Delivery found"));
    }

    @PutMapping("/update-status")
    public ResponseEntity<DeliveryResponse> updateDeliveryStatus(@RequestBody DeliveryUpdateRequest request) {
        Delivery delivery = deliveryService.updateDeliveryStatus(
                request.getTrackingNumber(),
                request.getNewStatus(),
                request.getCurrentLocation(),
                request.getNotes()
        );
        return ResponseEntity.ok(convertToDto(delivery, "Delivery status updated"));
    }

    private DeliveryResponse convertToDto(Delivery delivery, String message) {
        return DeliveryResponse.builder()
                .orderId(delivery.getOrderId())
                .trackingNumber(delivery.getTrackingNumber())
                .status(delivery.getStatus())
                .currentLocation(delivery.getCurrentLocation())
                .estimatedDeliveryDate(delivery.getEstimatedDeliveryDate())
                .actualDeliveryDate(delivery.getActualDeliveryDate())
                .recipientName(delivery.getRecipientName())
                .recipientAddress(delivery.getRecipientAddress())
                .deliveryAgent(delivery.getDeliveryAgent())
                .message(message)
                .build();
    }
}