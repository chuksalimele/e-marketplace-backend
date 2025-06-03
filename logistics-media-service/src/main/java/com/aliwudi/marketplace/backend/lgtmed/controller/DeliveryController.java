package com.aliwudi.marketplace.backend.lgtmed.controller;

import com.aliwudi.marketplace.backend.common.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryRequest;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryUpdateRequest;
import com.aliwudi.marketplace.backend.lgtmed.service.DeliveryService;
import com.aliwudi.marketplace.backend.lgtmed.exception.DeliveryNotFoundException;
import com.aliwudi.marketplace.backend.lgtmed.exception.InvalidDeliveryDataException;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux
import org.springframework.data.domain.Pageable; // For pagination
import org.springframework.data.domain.PageRequest; // For creating Pageable instances
import org.springframework.data.domain.Sort; // For sorting

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException; // For parsing dates in controller
import java.util.List;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.status.DeliveryStatus;


@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    /**
     * Helper method to map Delivery entity to Delivery DTO for public exposure.
     */
    private Mono<Delivery> prepareDto(Delivery delivery) {
        if (delivery == null) {
            return null;
        }
        return Mono.just(delivery);// COME BACK
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> createDelivery(@Valid @RequestBody DeliveryRequest request) {
        if (request.getOrderId() == null || request.getOrderId().isBlank() ||
            request.getRecipientName() == null || request.getRecipientName().isBlank() ||
            request.getRecipientAddress() == null || request.getRecipientAddress().isBlank() ||
            request.getDeliveryAgent() == null || request.getDeliveryAgent().isBlank() ||
            request.getEstimatedDeliveryDate() == null) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_DELIVERY_CREATION_REQUEST));
        }

        return deliveryService.createDelivery(
                request.getOrderId(), // String orderId
                request.getRecipientName(),
                request.getRecipientAddress(),
                request.getDeliveryAgent(),
                request.getEstimatedDeliveryDate()
            )
            .map(delivery -> (StandardResponseEntity) StandardResponseEntity.created(prepareDto(delivery), ApiResponseMessages.DELIVERY_CREATED_SUCCESS))
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

        return deliveryService.getDeliveryByOrderId(orderId) // String orderId
            .map(delivery -> (StandardResponseEntity) StandardResponseEntity.ok(prepareDto(delivery), ApiResponseMessages.DELIVERY_RETRIEVED_SUCCESS))
            .onErrorResume(DeliveryNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(InvalidDeliveryDataException.class, e -> // Catch for NumberFormatException from service
                    Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_DELIVERY_BY_ORDER + ": " + e.getMessage())));
    }

    @GetMapping("/track/{trackingNumber}")
    public Mono<StandardResponseEntity> getDeliveryByTrackingNumber(@PathVariable String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_TRACKING_NUMBER));
        }

        return deliveryService.getDeliveryByTrackingNumber(trackingNumber)
            .map(delivery -> (StandardResponseEntity) StandardResponseEntity.ok(prepareDto(delivery), ApiResponseMessages.DELIVERY_RETRIEVED_SUCCESS))
            .onErrorResume(DeliveryNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_DELIVERY_BY_TRACKING + ": " + e.getMessage())));
    }

    @PutMapping("/update-status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DELIVERY_AGENT')")
    public Mono<StandardResponseEntity> updateDeliveryStatus(@Valid @RequestBody DeliveryUpdateRequest request) {
        if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank() ||
            request.getNewStatus() == null || request.getNewStatus().name().isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_DELIVERY_STATUS_UPDATE_REQUEST));
        }

        return deliveryService.updateDeliveryStatus(
                request.getTrackingNumber(),
                request.getNewStatus().name(), // Pass status as String
                request.getCurrentLocation(),
                request.getNotes()
            )
            .map(delivery -> (StandardResponseEntity) StandardResponseEntity.ok(prepareDto(delivery), ApiResponseMessages.DELIVERY_STATUS_UPDATED_SUCCESS))
            .onErrorResume(DeliveryNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(InvalidDeliveryDataException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_DELIVERY_STATUS + ": " + e.getMessage())));
    }

    @PutMapping("/cancel/{trackingNumber}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> cancelDelivery(
            @PathVariable String trackingNumber,
            @RequestParam(required = false) String reason) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_TRACKING_NUMBER));
        }

        return deliveryService.cancelDelivery(trackingNumber, reason)
                .map(delivery -> (StandardResponseEntity) StandardResponseEntity.ok(prepareDto(delivery), ApiResponseMessages.DELIVERY_CANCELED_SUCCESS))
                .onErrorResume(DeliveryNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(InvalidDeliveryDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CANCELING_DELIVERY + ": " + e.getMessage())));
    }

    // --- NEW: Controller Endpoints for all DeliveryRepository methods ---

    /**
     * Endpoint to retrieve all delivery records with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Delivery records.
     */
    @GetMapping("/admin/all-paginated") // Renamed to avoid conflict with existing /
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<Delivery> getAllDeliveriesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findAllDeliveries(pageable)
                .map(this::prepareDto);
    }

    /**
     * Endpoint to find deliveries by their status with pagination.
     *
     * @param status The delivery status (e.g., "PENDING", "SHIPPED").
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Delivery records.
     */
    @GetMapping("/admin/byStatus/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DELIVERY_AGENT')")
    public Flux<Delivery> getDeliveriesByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        DeliveryStatus deliveryStatus;
        try {
            deliveryStatus = DeliveryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Flux.error(new InvalidDeliveryDataException("Invalid delivery status: " + status));
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findDeliveriesByStatus(deliveryStatus, pageable)
                .map(this::prepareDto);
    }

    /**
     * Endpoint to find deliveries by the delivery agent with pagination.
     *
     * @param deliveryAgent The name of the delivery agent.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Delivery records.
     */
    @GetMapping("/admin/byAgent/{deliveryAgent}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DELIVERY_AGENT')")
    public Flux<Delivery> getDeliveriesByDeliveryAgent(
            @PathVariable String deliveryAgent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findDeliveriesByDeliveryAgent(deliveryAgent, pageable)
                .map(this::prepareDto);
    }

    /**
     * Endpoint to find deliveries with an estimated delivery date before a specific date, with pagination.
     *
     * @param date The cutoff date (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Delivery records.
     */
    @GetMapping("/admin/estimatedBefore")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<Delivery> getDeliveriesByEstimatedDeliveryDateBefore(
            @RequestParam String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(date);
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            return deliveryService.findDeliveriesByEstimatedDeliveryDateBefore(dateTime, pageable)
                    .map(this::prepareDto);
        } catch (DateTimeParseException e) {
            return Flux.error(new InvalidDeliveryDataException("Invalid date format. Please use ISO 8601 format: YYYY-MM-ddTHH:mm:ss."));
        }
    }

    /**
     * Endpoint to find deliveries whose current location contains the specified string (case-insensitive), with pagination.
     *
     * @param location The location string to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Delivery records.
     */
    @GetMapping("/admin/byLocation")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<Delivery> getDeliveriesByCurrentLocationContaining(
            @RequestParam String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findDeliveriesByCurrentLocationContaining(location, pageable)
                .map(this::prepareDto);
    }

    /**
     * Endpoint to count all delivery records.
     *
     * @return A Mono emitting StandardResponseEntity with the total count.
     */
    @GetMapping("/count/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countAllDeliveries() {
        return deliveryService.countAllDeliveries()
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ALL_DELIVERIES + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count deliveries for a specific order ID.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byOrder/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> countDeliveriesByOrderId(@PathVariable String orderId) {
        return deliveryService.countDeliveriesByOrderId(orderId)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(InvalidDeliveryDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_DELIVERIES_BY_ORDER + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count deliveries by their status.
     *
     * @param status The delivery status.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byStatus/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DELIVERY_AGENT')")
    public Mono<StandardResponseEntity> countDeliveriesByStatus(@PathVariable String status) {
        DeliveryStatus deliveryStatus;
        try {
            deliveryStatus = DeliveryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest("Invalid delivery status: " + status));
        }
        return deliveryService.countDeliveriesByStatus(deliveryStatus)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_DELIVERIES_BY_STATUS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count deliveries by the delivery agent.
     *
     * @param deliveryAgent The name of the delivery agent.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byAgent/{deliveryAgent}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DELIVERY_AGENT')")
    public Mono<StandardResponseEntity> countDeliveriesByAgent(@PathVariable String deliveryAgent) {
        return deliveryService.countDeliveriesByAgent(deliveryAgent)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_DELIVERIES_BY_AGENT + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count deliveries with an estimated delivery date before a specific date.
     *
     * @param date The cutoff date (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/estimatedBefore")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countDeliveriesByEstimatedDeliveryDateBefore(@RequestParam String date) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(date);
            return deliveryService.countDeliveriesByEstimatedDeliveryDateBefore(dateTime)
                    .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                    .onErrorResume(Exception.class, e ->
                            Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_DELIVERIES_BY_ESTIMATED_DATE + ": " + e.getMessage())));
        } catch (DateTimeParseException e) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest("Invalid date format. Please use ISO 8601 format: YYYY-MM-ddTHH:mm:ss."));
        }
    }

    /**
     * Endpoint to count deliveries whose current location contains the specified string (case-insensitive).
     *
     * @param location The location string to search for.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byLocation")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countDeliveriesByCurrentLocationContaining(@RequestParam String location) {
        return deliveryService.countDeliveriesByCurrentLocationContaining(location)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.DELIVERY_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_DELIVERIES_BY_LOCATION + ": " + e.getMessage())));
    }

    /**
     * Endpoint to check if a delivery record exists for a given tracking number.
     *
     * @param trackingNumber The tracking number.
     * @return A Mono emitting StandardResponseEntity with a boolean indicating existence.
     */
    @GetMapping("/exists/{trackingNumber}")
    public Mono<StandardResponseEntity> existsByTrackingNumber(@PathVariable String trackingNumber) {
        return deliveryService.existsByTrackingNumber(trackingNumber)
                .map(exists -> (StandardResponseEntity) StandardResponseEntity.ok(exists, ApiResponseMessages.DELIVERY_EXISTS_CHECK_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CHECKING_DELIVERY_EXISTENCE + ": " + e.getMessage())));
    }

    /**
     * Endpoint to delete a delivery by tracking number.
     *
     * @param trackingNumber The tracking number of the delivery to delete.
     * @return A Mono<Void> indicating completion.
     */
    @DeleteMapping("/admin/delete/{trackingNumber}") // Renamed to avoid conflict with existing /
    @PreAuthorize("hasRole('ADMIN')")
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