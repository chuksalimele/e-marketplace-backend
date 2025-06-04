package com.aliwudi.marketplace.backend.lgtmed.service;

import com.aliwudi.marketplace.backend.common.intersevice.OrderIntegrationService;
import com.aliwudi.marketplace.backend.common.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.exception.DeliveryNotFoundException;
import com.aliwudi.marketplace.backend.lgtmed.exception.InvalidDeliveryDataException;
import com.aliwudi.marketplace.backend.lgtmed.repository.DeliveryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Import Slf4j for logging
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable; // Import for pagination

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException; // For parsing dates in service
import java.util.UUID;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.status.DeliveryStatus;

@Service
@RequiredArgsConstructor
@Slf4j // Add Slf4j for logging
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderIntegrationService orderIntegrationService;

    /**
     * Creates a new delivery record.
     *
     * @param orderIdStr The ID of the order (as String).
     * @param recipientName The name of the recipient.
     * @param recipientAddress The address of the recipient.
     * @param deliveryAgent The delivery agent.
     * @param estimatedDeliveryDate The estimated delivery date.
     * @return A Mono emitting the created Delivery.
     * @throws InvalidDeliveryDataException if order not found, delivery already exists, or data is invalid.
     */
    public Mono<Delivery> createDelivery(Long orderId, String recipientName, String recipientAddress, String deliveryAgent, LocalDateTime estimatedDeliveryDate) {
        try {
        } catch (NumberFormatException e) {
            return Mono.error(new InvalidDeliveryDataException("Invalid Order ID format: " + orderId));
        }

        // 1. Validate if the order exists
        return orderIntegrationService.orderExistsById(orderId)
                .flatMap(orderExists -> {
                    if (Boolean.FALSE.equals(orderExists)) {
                        return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.ORDER_NOT_FOUND +": "+ orderId));
                    }
                    // 2. Ensure only one delivery per order (if that's the business rule)
                    return deliveryRepository.findByOrderId(orderId)
                            .flatMap(existingDelivery -> Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.DELIVERY_ALREADY_EXISTS_FOR_ORDER +": "+ orderId)))
                            .switchIfEmpty(Mono.defer(() -> {
                                // 3. Create new Delivery
                                String trackingNumber = UUID.randomUUID().toString();
                                Delivery delivery = Delivery.builder()
                                        .orderId(orderId) // Use Long orderId
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
                    log.error("Error creating delivery for order {}: {}", orderId, e.getMessage());
                    if (e instanceof InvalidDeliveryDataException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.ERROR_CREATING_DELIVERY + ": " + e.getMessage()));
                });
    }

    /**
     * Retrieves a delivery by its associated order ID.
     * Converts String orderId to Long for repository interaction.
     *
     * @param orderIdStr The ID of the order (as String).
     * @return A Mono emitting the Delivery if found.
     * @throws DeliveryNotFoundException if no delivery is found for the given order ID.
     */
    public Mono<Delivery> getDeliveryByOrderId(String orderIdStr) {
        Long orderId;
        try {
            orderId = Long.parseLong(orderIdStr);
        } catch (NumberFormatException e) {
            return Mono.error(new InvalidDeliveryDataException("Invalid Order ID format: " + orderIdStr));
        }

        return deliveryRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_ORDER + orderIdStr)))
                .onErrorResume(e -> {
                    log.error("Error retrieving delivery by order ID {}: {}", orderIdStr, e.getMessage());
                    if (e instanceof DeliveryNotFoundException || e instanceof InvalidDeliveryDataException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_DELIVERY_BY_ORDER + ": " + e.getMessage()));
                });
    }

    /**
     * Retrieves a delivery by its tracking number.
     *
     * @param trackingNumber The tracking number of the delivery.
     * @return A Mono emitting the Delivery if found.
     * @throws DeliveryNotFoundException if no delivery is found for the given tracking number.
     */
    public Mono<Delivery> getDeliveryByTrackingNumber(String trackingNumber) {
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_TRACKING + trackingNumber)))
                .onErrorResume(e -> {
                    log.error("Error retrieving delivery by tracking number {}: {}", trackingNumber, e.getMessage());
                    if (e instanceof DeliveryNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_DELIVERY_BY_TRACKING + ": " + e.getMessage()));
                });
    }

    /**
     * Updates the status of an existing delivery.
     *
     * @param trackingNumber The tracking number of the delivery.
     * @param newStatus The new status for the delivery (as String).
     * @param currentLocation The current location of the delivery (optional).
     * @param notes Additional notes for the update (optional).
     * @return A Mono emitting the updated Delivery.
     * @throws DeliveryNotFoundException if the delivery is not found.
     * @throws InvalidDeliveryDataException if the new status is invalid or transition is not allowed.
     */
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
                        if (delivery.getStatus() == DeliveryStatus.CANCELLED && status != DeliveryStatus.CANCELLED) {
                            return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_TRANSITION_FROM_CANCELED));
                        }
                        // Prevent setting to DELIVERED if estimated date is in future (optional, but good practice)
                        if (status == DeliveryStatus.DELIVERED && delivery.getEstimatedDeliveryDate() != null && delivery.getEstimatedDeliveryDate().isAfter(LocalDateTime.now())) {
                             log.warn("Attempting to set status to DELIVERED before estimated date for tracking number: {}", trackingNumber);
                             // return Mono.error(new InvalidDeliveryDataException("Cannot set status to DELIVERED before estimated delivery date."));
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
                    log.error("Error updating delivery status for tracking number {}: {}", trackingNumber, e.getMessage());
                    if (e instanceof DeliveryNotFoundException || e instanceof InvalidDeliveryDataException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_UPDATING_DELIVERY_STATUS + ": " + e.getMessage()));
                });
    }

    /**
     * Cancels a delivery.
     *
     * @param trackingNumber The tracking number of the delivery to cancel.
     * @param cancellationReason Optional reason for cancellation.
     * @return A Mono emitting the updated Delivery.
     * @throws DeliveryNotFoundException if the delivery is not found.
     * @throws InvalidDeliveryDataException if the delivery cannot be cancelled in its current state.
     */
    public Mono<Delivery> cancelDelivery(String trackingNumber, String cancellationReason) {
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_CANCEL + trackingNumber)))
                .flatMap(delivery -> {
                    // Only allow cancellation if status is PENDING or SHIPPED (adjust as per business rules)
                    if (delivery.getStatus() == DeliveryStatus.DELIVERED || delivery.getStatus() == DeliveryStatus.CANCELLED || delivery.getStatus() == DeliveryStatus.FAILED) {
                        return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_FOR_CANCELLATION + delivery.getStatus().name()));
                    }

                    delivery.setStatus(DeliveryStatus.CANCELLED);
                    String newNotes = "Canceled. Reason: " + (cancellationReason != null && !cancellationReason.isBlank() ? cancellationReason : "No reason provided.");
                    String existingNotes = delivery.getNotes();
                    delivery.setNotes(existingNotes == null || existingNotes.isBlank() ? newNotes : existingNotes + "\n" + newNotes);
                    delivery.setUpdatedAt(LocalDateTime.now());
                    return deliveryRepository.save(delivery);
                })
                .onErrorResume(e -> {
                    log.error("Error canceling delivery {}: {}", trackingNumber, e.getMessage());
                    if (e instanceof DeliveryNotFoundException || e instanceof InvalidDeliveryDataException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_CANCELING_DELIVERY + ": " + e.getMessage()));
                });
    }

    // --- NEW: Implementations for all DeliveryRepository methods ---

    /**
     * Finds all delivery records with pagination.
     *
     * @param pageable Pagination information.
     * @return A Flux of Delivery records.
     */
    public Flux<Delivery> findAllDeliveries(Pageable pageable) {
        log.info("Finding all deliveries with pagination: {}", pageable);
        return deliveryRepository.findAllBy(pageable);
    }

    /**
     * Finds deliveries by their status with pagination.
     *
     * @param status The delivery status.
     * @param pageable Pagination information.
     * @return A Flux of Delivery records with the specified status.
     */
    public Flux<Delivery> findDeliveriesByStatus(DeliveryStatus status, Pageable pageable) {
        log.info("Finding deliveries with status: {} with pagination: {}", status, pageable);
        return deliveryRepository.findByStatus(status, pageable);
    }

    /**
     * Finds deliveries by the delivery agent with pagination.
     *
     * @param deliveryAgent The name of the delivery agent.
     * @param pageable Pagination information.
     * @return A Flux of Delivery records for the specified agent.
     */
    public Flux<Delivery> findDeliveriesByDeliveryAgent(String deliveryAgent, Pageable pageable) {
        log.info("Finding deliveries by agent: {} with pagination: {}", deliveryAgent, pageable);
        return deliveryRepository.findByDeliveryAgent(deliveryAgent, pageable);
    }

    /**
     * Finds deliveries with an estimated delivery date before a specific date, with pagination.
     *
     * @param date The cutoff date.
     * @param pageable Pagination information.
     * @return A Flux of Delivery records.
     */
    public Flux<Delivery> findDeliveriesByEstimatedDeliveryDateBefore(LocalDateTime date, Pageable pageable) {
        log.info("Finding deliveries with estimated delivery date before {} with pagination: {}", date, pageable);
        return deliveryRepository.findByEstimatedDeliveryDateBefore(date, pageable);
    }

    /**
     * Finds deliveries whose current location contains the specified string (case-insensitive), with pagination.
     *
     * @param location The location string to search for.
     * @param pageable Pagination information.
     * @return A Flux of Delivery records.
     */
    public Flux<Delivery> findDeliveriesByCurrentLocationContaining(String location, Pageable pageable) {
        log.info("Finding deliveries by current location containing '{}' with pagination: {}", location, pageable);
        return deliveryRepository.findByCurrentLocationContainingIgnoreCase(location, pageable);
    }

    /**
     * Counts all delivery records.
     *
     * @return A Mono emitting the total count of delivery records.
     */
    public Mono<Long> countAllDeliveries() {
        log.info("Counting all deliveries.");
        return deliveryRepository.count();
    }

    /**
     * Counts deliveries for a specific order ID.
     * Converts String orderId to Long for repository interaction.
     *
     * @param orderIdStr The ID of the order (as String).
     * @return A Mono emitting the count.
     * @throws InvalidDeliveryDataException if the orderId format is invalid.
     */
    public Mono<Long> countDeliveriesByOrderId(String orderIdStr) {
        Long orderId;
        try {
            orderId = Long.parseLong(orderIdStr);
        } catch (NumberFormatException e) {
            return Mono.error(new InvalidDeliveryDataException("Invalid Order ID format: " + orderIdStr));
        }
        log.info("Counting deliveries for order ID: {}", orderId);
        return deliveryRepository.countByOrderId(orderId);
    }

    /**
     * Counts deliveries by their status.
     *
     * @param status The delivery status.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countDeliveriesByStatus(DeliveryStatus status) {
        log.info("Counting deliveries with status: {}", status);
        return deliveryRepository.countByStatus(status);
    }

    /**
     * Counts deliveries by the delivery agent.
     *
     * @param deliveryAgent The name of the delivery agent.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countDeliveriesByAgent(String deliveryAgent) {
        log.info("Counting deliveries by agent: {}", deliveryAgent);
        return deliveryRepository.countByDeliveryAgent(deliveryAgent);
    }

    /**
     * Counts deliveries with an estimated delivery date before a specific date.
     *
     * @param date The cutoff date.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countDeliveriesByEstimatedDeliveryDateBefore(LocalDateTime date) {
        log.info("Counting deliveries with estimated delivery date before {}", date);
        return deliveryRepository.countByEstimatedDeliveryDateBefore(date);
    }

    /**
     * Counts deliveries whose current location contains the specified string (case-insensitive).
     *
     * @param location The location string to search for.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countDeliveriesByCurrentLocationContaining(String location) {
        log.info("Counting deliveries by current location containing '{}'", location);
        return deliveryRepository.countByCurrentLocationContainingIgnoreCase(location);
    }

    /**
     * Checks if a delivery record exists for a given tracking number.
     *
     * @param trackingNumber The tracking number.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> existsByTrackingNumber(String trackingNumber) {
        log.info("Checking if delivery exists for tracking number: {}", trackingNumber);
        return deliveryRepository.existsByTrackingNumber(trackingNumber);
    }

    /**
     * Deletes a delivery by its tracking number.
     *
     * @param trackingNumber The tracking number of the delivery to delete.
     * @return A Mono<Void> indicating completion.
     * @throws DeliveryNotFoundException if the delivery is not found.
     */
    public Mono<Void> deleteDelivery(String trackingNumber) {
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_DELETE + trackingNumber)))
                .flatMap(deliveryRepository::delete)
                .onErrorResume(e -> {
                    log.error("Error deleting delivery {}: {}", trackingNumber, e.getMessage());
                    if (e instanceof DeliveryNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_DELETING_DELIVERY + ": " + e.getMessage()));
                });
    }
}