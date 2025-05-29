package com.aliwudi.marketplace.backend.lgtmed.controller;

import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryRequest;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryResponse;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryUpdateRequest;
import com.aliwudi.marketplace.backend.lgtmed.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // NEW: Import Mono
import org.springframework.web.server.ResponseStatusException; // NEW: For reactive error handling

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping
    public Mono<ResponseEntity<DeliveryResponse>> createDelivery(@RequestBody DeliveryRequest request) {
        return deliveryService.createDelivery(
                request.getOrderId(),
                request.getRecipientName(),
                request.getRecipientAddress(),
                request.getDeliveryAgent(),
                request.getEstimatedDeliveryDate()
            )
            .map(delivery -> ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(delivery, "Delivery created successfully")))
            .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
    }

    @GetMapping("/order/{orderId}")
    public Mono<ResponseEntity<DeliveryResponse>> getDeliveryByOrderId(@PathVariable String orderId) {
        return deliveryService.getDeliveryByOrderId(orderId)
            .map(delivery -> ResponseEntity.ok(convertToDto(delivery, "Delivery found")))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found for orderId: " + orderId)))
            .onErrorResume(ResponseStatusException.class, Mono::error) // Re-throw as WebFlux exception
            .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
    }

    @GetMapping("/track/{trackingNumber}")
    public Mono<ResponseEntity<DeliveryResponse>> getDeliveryByTrackingNumber(@PathVariable String trackingNumber) {
        return deliveryService.getDeliveryByTrackingNumber(trackingNumber)
            .map(delivery -> ResponseEntity.ok(convertToDto(delivery, "Delivery found")))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found for trackingNumber: " + trackingNumber)))
            .onErrorResume(ResponseStatusException.class, Mono::error) // Re-throw as WebFlux exception
            .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
    }

    @PutMapping("/update-status")
    public Mono<ResponseEntity<DeliveryResponse>> updateDeliveryStatus(@RequestBody DeliveryUpdateRequest request) {
        return deliveryService.updateDeliveryStatus(
                request.getTrackingNumber(),
                request.getNewStatus(),
                request.getCurrentLocation(),
                request.getNotes()
            )
            .map(delivery -> ResponseEntity.ok(convertToDto(delivery, "Delivery status updated")))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found for update, trackingNumber: " + request.getTrackingNumber())))
            .onErrorResume(ResponseStatusException.class, Mono::error) // Re-throw as WebFlux exception
            .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
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