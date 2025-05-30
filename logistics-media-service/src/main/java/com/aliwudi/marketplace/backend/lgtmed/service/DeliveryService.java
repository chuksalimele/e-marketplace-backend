package com.aliwudi.marketplace.backend.lgtmed.service;

import com.aliwudi.marketplace.backend.lgtmed.exception.DeliveryNotFoundException;
import com.aliwudi.marketplace.backend.lgtmed.exception.InvalidDeliveryDataException;
import com.aliwudi.marketplace.backend.lgtmed.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.model.DeliveryStatus;
import com.aliwudi.marketplace.backend.lgtmed.repository.DeliveryRepository;
import com.aliwudi.marketplace.backend.order.repository.OrderRepository;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux; // NEW: Import Flux
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService{

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;

    
    public Mono<Delivery> createDelivery(String orderId, String recipientName, String recipientAddress, String deliveryAgent, LocalDateTime estimatedDeliveryDate) {
        // 1. Validate if the order exists
        return orderRepository.existsById(orderId)
                .flatMap(orderExists -> {
                    if (Boolean.FALSE.equals(orderExists)) {
                        return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.ORDER_NOT_FOUND + orderId));
                    }
                    // 2. Ensure only one delivery per order (if that's the business rule)
                    return deliveryRepository.findByOrderId(orderId)
                            .flatMap(existingDelivery -> Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.DELIVERY_ALREADY_EXISTS_FOR_ORDER + orderId)))
                            .switchIfEmpty(Mono.defer(() -> {
                                // 3. Create new Delivery
                                String trackingNumber = UUID.randomUUID().toString();
                                Delivery delivery = Delivery.builder()
                                        .orderId(orderId)
                                        .trackingNumber(trackingNumber)
                                        .recipientName(recipientName)
                                        .recipientAddress(recipientAddress)
                                        .deliveryAgent(deliveryAgent)
                                        .estimatedDeliveryDate(estimatedDeliveryDate)
                                        .status(DeliveryStatus.PENDING)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .notes("Delivery created.")
                                        .build();
                                return deliveryRepository.save(delivery);
                            }));
                })
                .cast(Delivery.class)
                .onErrorResume(e -> {
                    System.err.println("Error creating delivery for order " + orderId + ": " + e.getMessage());
                    if (e instanceof InvalidDeliveryDataException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.ERROR_CREATING_DELIVERY + ": " + e.getMessage()));
                });
    }

    
    public Mono<Delivery> getDeliveryByOrderId(String orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_ORDER + orderId)))
                .onErrorResume(e -> {
                    System.err.println("Error retrieving delivery by order ID " + orderId + ": " + e.getMessage());
                    if (e instanceof DeliveryNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_DELIVERY_BY_ORDER + ": " + e.getMessage()));
                });
    }

    
    public Mono<Delivery> getDeliveryByTrackingNumber(String trackingNumber) {
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_TRACKING + trackingNumber)))
                .onErrorResume(e -> {
                    System.err.println("Error retrieving delivery by tracking number " + trackingNumber + ": " + e.getMessage());
                    if (e instanceof DeliveryNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_DELIVERY_BY_TRACKING + ": " + e.getMessage()));
                });
    }

    
    public Mono<Delivery> updateDeliveryStatus(String trackingNumber, String newStatus, String currentLocation, String notes) {
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_UPDATE + trackingNumber)))
                .flatMap(delivery -> {
                    try {
                        DeliveryStatus status = DeliveryStatus.valueOf(newStatus.toUpperCase());

                        // Basic status transition validation (example: cannot go from DELIVERED to SHIPPED)
                        if (delivery.getStatus() == DeliveryStatus.DELIVERED && status != DeliveryStatus.DELIVERED) {
                            return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_TRANSITION_FROM_DELIVERED));
                        }
                        if (delivery.getStatus() == DeliveryStatus.CANCELED && status != DeliveryStatus.CANCELED) {
                            return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_TRANSITION_FROM_CANCELED));
                        }

                        delivery.setStatus(status);
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS + newStatus));
                    }

                    if (currentLocation != null && !currentLocation.isBlank()) {
                        delivery.setCurrentLocation(currentLocation);
                    }
                    if (notes != null && !notes.isBlank()) {
                        String existingNotes = delivery.getNotes();
                        delivery.setNotes(existingNotes == null || existingNotes.isBlank() ? notes : existingNotes + "\n" + notes);
                    }
                    delivery.setUpdatedAt(LocalDateTime.now());

                    if (delivery.getStatus() == DeliveryStatus.DELIVERED && delivery.getActualDeliveryDate() == null) {
                        delivery.setActualDeliveryDate(LocalDateTime.now());
                    }

                    return deliveryRepository.save(delivery);
                })
                .onErrorResume(e -> {
                    System.err.println("Error updating delivery status for tracking number " + trackingNumber + ": " + e.getMessage());
                    if (e instanceof DeliveryNotFoundException || e instanceof InvalidDeliveryDataException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_UPDATING_DELIVERY_STATUS + ": " + e.getMessage()));
                });
    }

    // NEW: Implement cancelDelivery
    
    public Mono<Delivery> cancelDelivery(String trackingNumber, String cancellationReason) {
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_CANCEL + trackingNumber)))
                .flatMap(delivery -> {
                    // Only allow cancellation if status is PENDING or SHIPPED (adjust as per business rules)
                    if (delivery.getStatus() == DeliveryStatus.DELIVERED || delivery.getStatus() == DeliveryStatus.CANCELED || delivery.getStatus() == DeliveryStatus.FAILED) {
                        return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_FOR_CANCELLATION + delivery.getStatus().name()));
                    }

                    delivery.setStatus(DeliveryStatus.CANCELED);
                    String newNotes = "Canceled. Reason: " + (cancellationReason != null && !cancellationReason.isBlank() ? cancellationReason : "No reason provided.");
                    String existingNotes = delivery.getNotes();
                    delivery.setNotes(existingNotes == null || existingNotes.isBlank() ? newNotes : existingNotes + "\n" + newNotes);
                    delivery.setUpdatedAt(LocalDateTime.now());
                    return deliveryRepository.save(delivery);
                })
                .onErrorResume(e -> {
                    System.err.println("Error canceling delivery " + trackingNumber + ": " + e.getMessage());
                    if (e instanceof DeliveryNotFoundException || e instanceof InvalidDeliveryDataException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_CANCELING_DELIVERY + ": " + e.getMessage()));
                });
    }

    // NEW: Implement getDeliveriesByAgent
    
    public Flux<Delivery> getDeliveriesByAgent(String deliveryAgent, Long offset, Integer limit) {
        if (deliveryAgent == null || deliveryAgent.isBlank()) {
            return Flux.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_AGENT));
        }
        return deliveryRepository.findByDeliveryAgent(deliveryAgent)
                .skip(offset)
                .take(limit)
                .onErrorResume(e -> {
                    System.err.println("Error retrieving deliveries by agent " + deliveryAgent + ": " + e.getMessage());
                    return Flux.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_DELIVERIES_BY_AGENT + ": " + e.getMessage()));
                });
    }

    // NEW: Implement countDeliveriesByAgent
    
    public Mono<Long> countDeliveriesByAgent(String deliveryAgent) {
        if (deliveryAgent == null || deliveryAgent.isBlank()) {
            return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_AGENT));
        }
        return deliveryRepository.countByDeliveryAgent(deliveryAgent)
                .onErrorResume(e -> {
                    System.err.println("Error counting deliveries by agent " + deliveryAgent + ": " + e.getMessage());
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_COUNTING_DELIVERIES_BY_AGENT + ": " + e.getMessage()));
                });
    }

    // NEW: Implement searchDeliveries
    
    public Flux<Delivery> searchDeliveries(String query, Long offset, Integer limit) {
        if (query == null || query.isBlank()) {
            return Flux.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_SEARCH_TERM));
        }
        return deliveryRepository.findByRecipientNameContainingIgnoreCaseOrRecipientAddressContainingIgnoreCaseOrDeliveryAgentContainingIgnoreCase(
                        query, query, query)
                .skip(offset)
                .take(limit)
                .onErrorResume(e -> {
                    System.err.println("Error searching deliveries for query " + query + ": " + e.getMessage());
                    return Flux.error(new RuntimeException(ApiResponseMessages.ERROR_SEARCHING_DELIVERIES + ": " + e.getMessage()));
                });
    }

    // NEW: Implement countSearchDeliveries
    
    public Mono<Long> countSearchDeliveries(String query) {
        if (query == null || query.isBlank()) {
            return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_SEARCH_TERM));
        }
        return deliveryRepository.countByRecipientNameContainingIgnoreCaseOrRecipientAddressContainingIgnoreCaseOrDeliveryAgentContainingIgnoreCase(
                        query, query, query)
                .onErrorResume(e -> {
                    System.err.println("Error counting search results for deliveries query " + query + ": " + e.getMessage());
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_COUNTING_SEARCH_DELIVERIES + ": " + e.getMessage()));
                });
    }

    // NEW: Implement getAllDeliveries
    
    public Flux<Delivery> getAllDeliveries(Long offset, Integer limit) {
        return deliveryRepository.findAll()
                .skip(offset)
                .take(limit)
                .onErrorResume(e -> {
                    System.err.println("Error retrieving all deliveries: " + e.getMessage());
                    return Flux.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_ALL_DELIVERIES + ": " + e.getMessage()));
                });
    }

    // NEW: Implement countAllDeliveries
    
    public Mono<Long> countAllDeliveries() {
        return deliveryRepository.count()
                .onErrorResume(e -> {
                    System.err.println("Error counting all deliveries: " + e.getMessage());
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_COUNTING_ALL_DELIVERIES + ": " + e.getMessage()));
                });
    }

    // NEW: Implement deleteDelivery (by tracking number for consistency)
    
    public Mono<Void> deleteDelivery(String trackingNumber) {
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_DELETE + trackingNumber)))
                .flatMap(deliveryRepository::delete)
                .onErrorResume(e -> {
                    System.err.println("Error deleting delivery " + trackingNumber + ": " + e.getMessage());
                    if (e instanceof DeliveryNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_DELETING_DELIVERY + ": " + e.getMessage()));
                });
    }
}