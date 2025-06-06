package com.aliwudi.marketplace.backend.lgtmed.service;

import com.aliwudi.marketplace.backend.common.intersevice.OrderIntegrationService;
import com.aliwudi.marketplace.backend.common.model.Delivery;
import com.aliwudi.marketplace.backend.common.model.Order; // Import Order model for prepareDto
import com.aliwudi.marketplace.backend.common.exception.DeliveryNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.InvalidDeliveryDataException;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.lgtmed.repository.DeliveryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List; // For prepareDto List.of
import java.util.UUID;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.status.DeliveryStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderIntegrationService orderIntegrationService;

    // IMPORTANT: This prepareDto method is moved from the controller
    // and kept *exactly* as provided by you. It is now a private helper method
    // within the service to enrich the entities before they are returned.
    /**
     * Helper method to map Delivery entity to Delivery DTO for public exposure.
     * This method enriches the Delivery object with Order details
     * by making integration calls.
     */
    private Mono<Delivery> prepareDto(Delivery delivery) {
        if (delivery == null) {
            return Mono.empty();
        }
        List<Mono<?>> listMonos = new java.util.ArrayList<>(); // Use ArrayList for mutable list

        // Fetch Order if not already set
        if (delivery.getOrder() == null && delivery.getOrderId() != null) {
            Mono<Order> orderMono = orderIntegrationService.getOrderById(delivery.getOrderId())
                .doOnNext(delivery::setOrder)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch order {} for delivery {}: {}", delivery.getOrderId(), delivery.getTrackingNumber(), e.getMessage());
                    delivery.setOrder(null); // Set to null if fetching fails
                    return Mono.empty(); // Continue with other enrichments
                });
            listMonos.add(orderMono);
        }

        if (listMonos.isEmpty()) {
            return Mono.just(delivery);
        }

        return Mono.zip(listMonos, (Object[] array) -> delivery)
                   .defaultIfEmpty(delivery); // Return delivery even if zipping produces no results (e.g., order not found)
    }

    /**
     * Creates a new delivery record.
     *
     * @param orderId The ID of the order.
     * @param recipientName The name of the recipient.
     * @param recipientAddress The address of the recipient.
     * @param deliveryAgent The delivery agent.
     * @param estimatedDeliveryDate The estimated delivery date.
     * @return A Mono emitting the created Delivery (enriched).
     * @throws InvalidDeliveryDataException if order not found, delivery already exists, or data is invalid.
     * @throws ResourceNotFoundException if the order does not exist.
     */
    public Mono<Delivery> createDelivery(Long orderId, String recipientName, String recipientAddress, String deliveryAgent, LocalDateTime estimatedDeliveryDate) {
        log.info("Attempting to create delivery for order ID: {}", orderId);

        // 1. Validate if the order exists (using OrderIntegrationService)
        return orderIntegrationService.orderExistsById(orderId)
                .flatMap(orderExists -> {
                    if (Boolean.FALSE.equals(orderExists)) {
                        log.warn("Order not found for delivery creation: {}", orderId);
                        return Mono.error(new ResourceNotFoundException(ApiResponseMessages.ORDER_NOT_FOUND + orderId));
                    }
                    // 2. Ensure only one delivery per order (if that's the business rule)
                    return deliveryRepository.findByOrderId(orderId)
                            .flatMap(existingDelivery -> {
                                log.warn("Delivery already exists for order ID: {}", orderId);
                                return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.DELIVERY_ALREADY_EXISTS_FOR_ORDER + orderId));
                            })
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
                .cast(Delivery.class) // Cast to Delivery after switchIfEmpty block
                .flatMap(this::prepareDto) // Enrich the created delivery
                .doOnSuccess(d -> log.info("Delivery created successfully with tracking number: {}", d.getTrackingNumber()))
                .doOnError(e -> log.error("Error creating delivery for order {}: {}", orderId, e.getMessage(), e));
    }

    /**
     * Retrieves a delivery by its associated order ID, enriching it.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the Delivery if found (enriched).
     * @throws DeliveryNotFoundException if no delivery is found for the given order ID.
     */
    public Mono<Delivery> getDeliveryByOrderId(Long orderId) {
        log.info("Retrieving delivery by order ID: {}", orderId);
        return deliveryRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_ORDER + orderId)))
                .flatMap(this::prepareDto) // Enrich the delivery
                .doOnSuccess(d -> log.info("Delivery for order ID {} retrieved successfully: {}", orderId, d.getTrackingNumber()))
                .doOnError(e -> log.error("Error retrieving delivery by order ID {}: {}", orderId, e.getMessage(), e));
    }

    /**
     * Retrieves a delivery by its tracking number, enriching it.
     *
     * @param trackingNumber The tracking number of the delivery.
     * @return A Mono emitting the Delivery if found (enriched).
     * @throws DeliveryNotFoundException if no delivery is found for the given tracking number.
     */
    public Mono<Delivery> getDeliveryByTrackingNumber(String trackingNumber) {
        log.info("Retrieving delivery by tracking number: {}", trackingNumber);
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_TRACKING + trackingNumber)))
                .flatMap(this::prepareDto) // Enrich the delivery
                .doOnSuccess(d -> log.info("Delivery retrieved successfully: {}", d.getTrackingNumber()))
                .doOnError(e -> log.error("Error retrieving delivery by tracking number {}: {}", trackingNumber, e.getMessage(), e));
    }

    /**
     * Updates the status of an existing delivery.
     * This operation is transactional.
     *
     * @param trackingNumber The tracking number of the delivery.
     * @param newStatus The new status for the delivery (as String).
     * @param currentLocation The current location of the delivery (optional).
     * @param notes Additional notes for the update (optional).
     * @return A Mono emitting the updated Delivery (enriched).
     * @throws DeliveryNotFoundException if the delivery is not found.
     * @throws InvalidDeliveryDataException if the new status is invalid or transition is not allowed.
     */
    public Mono<Delivery> updateDeliveryStatus(String trackingNumber, String newStatus, String currentLocation, String notes) {
        log.info("Attempting to update delivery status for tracking number: {} to {}", trackingNumber, newStatus);
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_UPDATE + trackingNumber)))
                .flatMap(delivery -> {
                    try {
                        DeliveryStatus status = DeliveryStatus.valueOf(newStatus.toUpperCase());

                        // Basic status transition validation (example: cannot go from DELIVERED to SHIPPED)
                        if (delivery.getStatus() == DeliveryStatus.DELIVERED && status != DeliveryStatus.DELIVERED) {
                            log.warn("Invalid status transition: DELIVERED to {} for tracking number {}", status, trackingNumber);
                            return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_TRANSITION_FROM_DELIVERED));
                        }
                        if (delivery.getStatus() == DeliveryStatus.CANCELLED && status != DeliveryStatus.CANCELLED) {
                            log.warn("Invalid status transition: CANCELLED to {} for tracking number {}", status, trackingNumber);
                            return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_TRANSITION_FROM_CANCELED));
                        }
                        if (delivery.getStatus() == DeliveryStatus.FAILED && status != DeliveryStatus.FAILED) {
                            log.warn("Invalid status transition: FAILED to {} for tracking number {}", status, trackingNumber);
                            return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_TRANSITION_FROM_FAILED));
                        }
                        // Prevent setting to DELIVERED if estimated date is in future (optional, but good practice)
                        if (status == DeliveryStatus.DELIVERED && delivery.getEstimatedDeliveryDate() != null && delivery.getEstimatedDeliveryDate().isAfter(LocalDateTime.now())) {
                             log.warn("Attempting to set status to DELIVERED before estimated date for tracking number: {}", trackingNumber);
                             // return Mono.error(new InvalidDeliveryDataException("Cannot set status to DELIVERED before estimated delivery date.")); // Decide whether to block or warn
                        }

                        delivery.setStatus(status);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid delivery status provided for tracking number {}: {}", trackingNumber, newStatus);
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
                .flatMap(this::prepareDto) // Enrich the updated delivery
                .doOnSuccess(d -> log.info("Delivery status updated successfully for tracking number {}. New status: {}", trackingNumber, d.getStatus()))
                .doOnError(e -> log.error("Error updating delivery status for tracking number {}: {}", trackingNumber, e.getMessage(), e));
    }

    /**
     * Cancels a delivery.
     * This operation is transactional.
     *
     * @param trackingNumber The tracking number of the delivery to cancel.
     * @param cancellationReason Optional reason for cancellation.
     * @return A Mono emitting the updated Delivery (enriched).
     * @throws DeliveryNotFoundException if the delivery is not found.
     * @throws InvalidDeliveryDataException if the delivery cannot be cancelled in its current state.
     */
    public Mono<Delivery> cancelDelivery(String trackingNumber, String cancellationReason) {
        log.info("Attempting to cancel delivery with tracking number: {}", trackingNumber);
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_CANCEL + trackingNumber)))
                .flatMap(delivery -> {
                    // Only allow cancellation if status is PENDING or SHIPPED (adjust as per business rules)
                    if (delivery.getStatus() == DeliveryStatus.DELIVERED || delivery.getStatus() == DeliveryStatus.CANCELLED || delivery.getStatus() == DeliveryStatus.FAILED) {
                        log.warn("Invalid delivery status for cancellation for tracking number {}: Current status {}", trackingNumber, delivery.getStatus());
                        return Mono.error(new InvalidDeliveryDataException(ApiResponseMessages.INVALID_DELIVERY_STATUS_FOR_CANCELLATION + delivery.getStatus().name()));
                    }

                    delivery.setStatus(DeliveryStatus.CANCELLED);
                    String newNotes = "Canceled. Reason: " + (cancellationReason != null && !cancellationReason.isBlank() ? cancellationReason : "No reason provided.");
                    String existingNotes = delivery.getNotes();
                    delivery.setNotes(existingNotes == null || existingNotes.isBlank() ? newNotes : existingNotes + "\n" + newNotes);
                    delivery.setUpdatedAt(LocalDateTime.now());
                    return deliveryRepository.save(delivery);
                })
                .flatMap(this::prepareDto) // Enrich the updated delivery
                .doOnSuccess(d -> log.info("Delivery canceled successfully for tracking number: {}", d.getTrackingNumber()))
                .doOnError(e -> log.error("Error canceling delivery {}: {}", trackingNumber, e.getMessage(), e));
    }

    /**
     * Deletes a delivery by its tracking number.
     * This operation is transactional.
     *
     * @param trackingNumber The tracking number of the delivery to delete.
     * @return A Mono<Void> indicating completion.
     * @throws DeliveryNotFoundException if the delivery is not found.
     */
    public Mono<Void> deleteDelivery(String trackingNumber) {
        log.info("Attempting to delete delivery with tracking number: {}", trackingNumber);
        return deliveryRepository.findByTrackingNumber(trackingNumber)
                .switchIfEmpty(Mono.error(new DeliveryNotFoundException(ApiResponseMessages.DELIVERY_NOT_FOUND_FOR_DELETE + trackingNumber)))
                .flatMap(deliveryRepository::delete)
                .doOnSuccess(v -> log.info("Delivery deleted successfully with tracking number: {}", trackingNumber))
                .doOnError(e -> log.error("Error deleting delivery {}: {}", trackingNumber, e.getMessage(), e));
    }

    // --- NEW: Implementations for all DeliveryRepository methods (with logging and error handling) ---

    /**
     * Finds all delivery records with pagination, enriching each.
     *
     * @param pageable Pagination information.
     * @return A Flux of Delivery records (enriched).
     */
    public Flux<Delivery> findAllDeliveries(Pageable pageable) {
        log.info("Finding all deliveries with pagination: {}", pageable);
        return deliveryRepository.findAllBy(pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving all deliveries for page {} with size {}.", pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving all deliveries: {}", e.getMessage(), e));
    }

    /**
     * Finds deliveries by their status with pagination, enriching each.
     *
     * @param status The delivery status.
     * @param pageable Pagination information.
     * @return A Flux of Delivery records with the specified status (enriched).
     */
    public Flux<Delivery> findDeliveriesByStatus(DeliveryStatus status, Pageable pageable) {
        log.info("Finding deliveries with status: {} with pagination: {}", status, pageable);
        return deliveryRepository.findByStatus(status, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving deliveries by status '{}' for page {} with size {}.", status, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving deliveries by status {}: {}", status, e.getMessage(), e));
    }

    /**
     * Finds deliveries by the delivery agent with pagination, enriching each.
     *
     * @param deliveryAgent The name of the delivery agent.
     * @param pageable Pagination information.
     * @return A Flux of Delivery records for the specified agent (enriched).
     */
    public Flux<Delivery> findDeliveriesByDeliveryAgent(String deliveryAgent, Pageable pageable) {
        log.info("Finding deliveries by agent: {} with pagination: {}", deliveryAgent, pageable);
        return deliveryRepository.findByDeliveryAgent(deliveryAgent, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving deliveries by agent '{}' for page {} with size {}.", deliveryAgent, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving deliveries by agent {}: {}", deliveryAgent, e.getMessage(), e));
    }

    /**
     * Finds deliveries with an estimated delivery date before a specific date, with pagination, enriching each.
     *
     * @param date The cutoff date.
     * @param pageable Pagination information.
     * @return A Flux of Delivery records (enriched).
     */
    public Flux<Delivery> findDeliveriesByEstimatedDeliveryDateBefore(LocalDateTime date, Pageable pageable) {
        log.info("Finding deliveries with estimated delivery date before {} with pagination: {}", date, pageable);
        return deliveryRepository.findByEstimatedDeliveryDateBefore(date, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving deliveries by estimated date before '{}' for page {} with size {}.", date, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving deliveries by estimated date before {}: {}", date, e.getMessage(), e));
    }

    /**
     * Finds deliveries whose current location contains the specified string (case-insensitive), with pagination, enriching each.
     *
     * @param location The location string to search for.
     * @param pageable Pagination information.
     * @return A Flux of Delivery records (enriched).
     */
    public Flux<Delivery> findDeliveriesByCurrentLocationContaining(String location, Pageable pageable) {
        log.info("Finding deliveries by current location containing '{}' with pagination: {}", location, pageable);
        return deliveryRepository.findByCurrentLocationContainingIgnoreCase(location, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving deliveries by current location containing '{}' for page {} with size {}.", location, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving deliveries by current location {}: {}", location, e.getMessage(), e));
    }

    /**
     * Counts all delivery records.
     *
     * @return A Mono emitting the total count of delivery records.
     */
    public Mono<Long> countAllDeliveries() {
        log.info("Counting all deliveries.");
        return deliveryRepository.count()
                .doOnSuccess(count -> log.info("Total delivery count: {}", count))
                .doOnError(e -> log.error("Error counting all deliveries: {}", e.getMessage(), e));
    }

    /**
     * Counts deliveries for a specific order ID.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the count.
     * @throws InvalidDeliveryDataException if the orderId format is invalid (though conversion should happen in controller).
     */
    public Mono<Long> countDeliveriesByOrderId(Long orderId) {
        log.info("Counting deliveries for order ID: {}", orderId);
        return deliveryRepository.countByOrderId(orderId)
                .doOnSuccess(count -> log.info("Total delivery count for order {}: {}", orderId, count))
                .doOnError(e -> log.error("Error counting deliveries for order {}: {}", orderId, e.getMessage(), e));
    }

    /**
     * Counts deliveries by their status.
     *
     * @param status The delivery status.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countDeliveriesByStatus(DeliveryStatus status) {
        log.info("Counting deliveries with status: {}", status);
        return deliveryRepository.countByStatus(status)
                .doOnSuccess(count -> log.info("Total delivery count for status {}: {}", status, count))
                .doOnError(e -> log.error("Error counting deliveries by status {}: {}", status, e.getMessage(), e));
    }

    /**
     * Counts deliveries by the delivery agent.
     *
     * @param deliveryAgent The name of the delivery agent.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countDeliveriesByAgent(String deliveryAgent) {
        log.info("Counting deliveries by agent: {}", deliveryAgent);
        return deliveryRepository.countByDeliveryAgent(deliveryAgent)
                .doOnSuccess(count -> log.info("Total delivery count for agent {}: {}", deliveryAgent, count))
                .doOnError(e -> log.error("Error counting deliveries by agent {}: {}", deliveryAgent, e.getMessage(), e));
    }

    /**
     * Counts deliveries with an estimated delivery date before a specific date.
     *
     * @param date The cutoff date.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countDeliveriesByEstimatedDeliveryDateBefore(LocalDateTime date) {
        log.info("Counting deliveries with estimated delivery date before {}", date);
        return deliveryRepository.countByEstimatedDeliveryDateBefore(date)
                .doOnSuccess(count -> log.info("Total delivery count for estimated date before {}: {}", date, count))
                .doOnError(e -> log.error("Error counting deliveries by estimated date before {}: {}", date, e.getMessage(), e));
    }

    /**
     * Counts deliveries whose current location contains the specified string (case-insensitive).
     *
     * @param location The location string to search for.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countDeliveriesByCurrentLocationContaining(String location) {
        log.info("Counting deliveries by current location containing '{}'", location);
        return deliveryRepository.countByCurrentLocationContainingIgnoreCase(location)
                .doOnSuccess(count -> log.info("Total delivery count for location '{}': {}", location, count))
                .doOnError(e -> log.error("Error counting deliveries by current location {}: {}", location, e.getMessage(), e));
    }

    /**
     * Checks if a delivery record exists for a given tracking number.
     *
     * @param trackingNumber The tracking number.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> existsByTrackingNumber(String trackingNumber) {
        log.info("Checking if delivery exists for tracking number: {}", trackingNumber);
        return deliveryRepository.existsByTrackingNumber(trackingNumber)
                .doOnSuccess(exists -> log.info("Delivery for tracking number {} exists: {}", trackingNumber, exists))
                .doOnError(e -> log.error("Error checking delivery existence for tracking number {}: {}", trackingNumber, e.getMessage(), e));
    }
}
