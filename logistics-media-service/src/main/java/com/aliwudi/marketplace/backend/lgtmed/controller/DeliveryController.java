package com.aliwudi.marketplace.backend.lgtmed.controller;

import com.aliwudi.marketplace.backend.common.dto.DeliveryDto;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryRequest;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryUpdateRequest;
import com.aliwudi.marketplace.backend.lgtmed.service.DeliveryService;
import com.aliwudi.marketplace.backend.lgtmed.model.Delivery; // Import Delivery for mapDeliveryToDeliveryDto
import com.aliwudi.marketplace.backend.lgtmed.exception.DeliveryNotFoundException;
import com.aliwudi.marketplace.backend.lgtmed.exception.InvalidDeliveryDataException;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;

import jakarta.validation.Valid; 
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize; // Assuming security is applied here
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List; // For Flux.collectList()
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    /**
     * Helper method to map Delivery entity to DeliveryDto DTO for public exposure.
     */
    private DeliveryDto mapDeliveryToDeliveryDto(Delivery delivery) {
        if (delivery == null) {
            return null;
        }
        return DeliveryDto.builder()
                .orderId(delivery.getOrderId())
                .trackingNumber(delivery.getTrackingNumber())
                .status(delivery.getStatus())
                .currentLocation(delivery.getCurrentLocation())
                .estimatedDeliveryDate(delivery.getEstimatedDeliveryDate())
                .actualDeliveryDate(delivery.getActualDeliveryDate())
                .recipientName(delivery.getRecipientName())
                .recipientAddress(delivery.getRecipientAddress())
                .deliveryAgent(delivery.getDeliveryAgent())
                .notes(delivery.getNotes()) // Include notes in response
                .build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')") // Assuming only admin/seller can create deliveries
    public Mono<StandardResponseEntity> createDelivery(@Valid @RequestBody DeliveryRequest request) {
        if (request.getOrderId() == null || request.getOrderId().isBlank() ||
            request.getRecipientName() == null || request.getRecipientName().isBlank() ||
            request.getRecipientAddress() == null || request.getRecipientAddress().isBlank() ||
            request.getDeliveryAgent() == null || request.getDeliveryAgent().isBlank() ||
            request.getEstimatedDeliveryDate() == null) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_DELIVERY_CREATION_REQUEST));
        }

        return deliveryService.createDelivery(
                request.getOrderId(),
                request.getRecipientName(),
                request.getRecipientAddress(),
                request.getDeliveryAgent(),
                request.getEstimatedDeliveryDate()
            )
            .map(delivery -> (StandardResponseEntity) StandardResponseEntity.created(mapDeliveryToDeliveryDto(delivery), ApiResponseMessages.DELIVERY_CREATED_SUCCESS))
            .onErrorResume(InvalidDeliveryDataException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CREATING_DELIVERY + ": " + e.getMessage())));
    }

    @GetMapping("/order/{orderId}")
    public Mono<StandardResponseEntity> getDeliveryByOrderId(@PathVariable String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ORDER_ID));
        }

        return deliveryService.getDeliveryByOrderId(orderId)
            .map(delivery -> (StandardResponseEntity) StandardResponseEntity.ok(mapDeliveryToDeliveryDto(delivery), ApiResponseMessages.DELIVERY_RETRIEVED_SUCCESS))
            .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_ORDER + orderId)))
            .onErrorResume(DeliveryNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_DELIVERY_BY_ORDER + ": " + e.getMessage())));
    }

    @GetMapping("/track/{trackingNumber}")
    public Mono<StandardResponseEntity> getDeliveryByTrackingNumber(@PathVariable String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_TRACKING_NUMBER));
        }

        return deliveryService.getDeliveryByTrackingNumber(trackingNumber)
            .map(delivery -> (StandardResponseEntity) StandardResponseEntity.ok(mapDeliveryToDeliveryDto(delivery), ApiResponseMessages.DELIVERY_RETRIEVED_SUCCESS))
            .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_TRACKING + trackingNumber)))
            .onErrorResume(DeliveryNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_DELIVERY_BY_TRACKING + ": " + e.getMessage())));
    }

    @PutMapping("/update-status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DELIVERY_AGENT')") // Only admin/delivery agent can update status
    public Mono<StandardResponseEntity> updateDeliveryStatus(@Valid @RequestBody DeliveryUpdateRequest request) {
        if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank() ||
            request.getNewStatus() == null || request.getNewStatus().name().isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_DELIVERY_STATUS_UPDATE_REQUEST));
        }

        return deliveryService.updateDeliveryStatus(
                request.getTrackingNumber(),
                request.getNewStatus().name(),
                request.getCurrentLocation(),
                request.getNotes()
            )
            .map(delivery -> (StandardResponseEntity) StandardResponseEntity.ok(mapDeliveryToDeliveryDto(delivery), ApiResponseMessages.DELIVERY_STATUS_UPDATED_SUCCESS))
            .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_UPDATE + request.getTrackingNumber())))
            .onErrorResume(DeliveryNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(InvalidDeliveryDataException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_DELIVERY_STATUS + ": " + e.getMessage())));
    }

    // NEW: Cancel Delivery endpoint
    @PutMapping("/cancel/{trackingNumber}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')") // Admin or seller can cancel
    public Mono<StandardResponseEntity> cancelDelivery(
            @PathVariable String trackingNumber,
            @RequestParam(required = false) String reason) { // Optional cancellation reason
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_TRACKING_NUMBER));
        }

        return deliveryService.cancelDelivery(trackingNumber, reason)
                .map(delivery -> (StandardResponseEntity) StandardResponseEntity.ok(mapDeliveryToDeliveryDto(delivery), ApiResponseMessages.DELIVERY_CANCELED_SUCCESS))
                .onErrorResume(DeliveryNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(InvalidDeliveryDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CANCELING_DELIVERY + ": " + e.getMessage())));
    }

    // NEW: Get Deliveries by Agent endpoint
    @GetMapping("/agent/{deliveryAgent}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DELIVERY_AGENT')") // Admin or delivery agent can view their deliveries
    public Mono<StandardResponseEntity> getDeliveriesByAgent(
            @PathVariable String deliveryAgent,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "10") Integer limit) {
        if (deliveryAgent == null || deliveryAgent.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_DELIVERY_AGENT));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return deliveryService.getDeliveriesByAgent(deliveryAgent, offset, limit)
                .map(this::mapDeliveryToDeliveryDto)
                .collectList()
                .map(deliveries -> (StandardResponseEntity) StandardResponseEntity.ok(deliveries, ApiResponseMessages.DELIVERIES_RETRIEVED_SUCCESS))
                .onErrorResume(InvalidDeliveryDataException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_DELIVERIES_BY_AGENT + ": " + e.getMessage())));
    }

    // NEW: Count Deliveries by Agent endpoint
    @GetMapping("/agent/{deliveryAgent}/count")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DELIVERY_AGENT')")
    public Mono<StandardResponseEntity> countDeliveriesByAgent(@PathVariable String deliveryAgent) {
        if (deliveryAgent == null || deliveryAgent.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_DELIVERY_AGENT));
        }

        return deliveryService.countDeliveriesByAgent(deliveryAgent)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(InvalidDeliveryDataException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_DELIVERIES_BY_AGENT + ": " + e.getMessage())));
    }

    // NEW: Search Deliveries endpoint
    @GetMapping("/search")
    public Mono<StandardResponseEntity> searchDeliveries(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "10") Integer limit) {
        if (query == null || query.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return deliveryService.searchDeliveries(query, offset, limit)
                .map(this::mapDeliveryToDeliveryDto)
                .collectList()
                .map(deliveries -> (StandardResponseEntity) StandardResponseEntity.ok(deliveries, ApiResponseMessages.DELIVERIES_RETRIEVED_SUCCESS))
                .onErrorResume(InvalidDeliveryDataException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_DELIVERIES + ": " + e.getMessage())));
    }

    // NEW: Count Search Deliveries endpoint
    @GetMapping("/search/count")
    public Mono<StandardResponseEntity> countSearchDeliveries(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }

        return deliveryService.countSearchDeliveries(query)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(InvalidDeliveryDataException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_SEARCH_DELIVERIES + ": " + e.getMessage())));
    }

    // NEW: Get All Deliveries endpoint (for admin overview)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')") // Only admin can see all deliveries
    public Mono<StandardResponseEntity> getAllDeliveries(
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "10") Integer limit) {
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return deliveryService.getAllDeliveries(offset, limit)
                .map(this::mapDeliveryToDeliveryDto)
                .collectList()
                .map(deliveries -> (StandardResponseEntity) StandardResponseEntity.ok(deliveries, ApiResponseMessages.DELIVERIES_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ALL_DELIVERIES + ": " + e.getMessage())));
    }

    // NEW: Count All Deliveries endpoint
    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countAllDeliveries() {
        return deliveryService.countAllDeliveries()
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ALL_DELIVERIES + ": " + e.getMessage())));
    }

    // NEW: Delete Delivery endpoint (by tracking number for consistency)
    @DeleteMapping("/{trackingNumber}")
    @PreAuthorize("hasRole('ADMIN')") // Only admin can delete deliveries
    public Mono<StandardResponseEntity> deleteDelivery(@PathVariable String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_TRACKING_NUMBER));
        }

        return deliveryService.deleteDelivery(trackingNumber)
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.DELIVERY_DELETED_SUCCESS)))
                .onErrorResume(DeliveryNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_DELIVERY + ": " + e.getMessage())));
    }
}