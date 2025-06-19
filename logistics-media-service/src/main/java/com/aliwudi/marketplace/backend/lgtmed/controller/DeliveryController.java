package com.aliwudi.marketplace.backend.lgtmed.controller;

import com.aliwudi.marketplace.backend.common.model.Delivery;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryRequest;
import com.aliwudi.marketplace.backend.lgtmed.dto.DeliveryUpdateRequest;
import com.aliwudi.marketplace.backend.lgtmed.service.DeliveryService;
import com.aliwudi.marketplace.backend.common.exception.DeliveryNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.InvalidDeliveryDataException;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.status.DeliveryStatus; // For DeliveryStatus enum

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; // For @ResponseStatus
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

// Static import for API path constants and roles
import static com.aliwudi.marketplace.backend.common.constants.ApiPaths.*;

@RestController
@RequestMapping(DELIVERY_CONTROLLER_BASE) // MODIFIED
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    // Removed direct injection of OrderIntegrationService as its usage is now confined to DeliveryService's prepareDto

    /**
     * Endpoint to create a new delivery.
     *
     * @param request The DeliveryRequest DTO containing delivery creation data.
     * @return A Mono emitting the created Delivery.
     * @throws IllegalArgumentException if input validation fails.
     * @throws InvalidDeliveryDataException if delivery data is invalid (e.g., order not found, duplicate delivery).
     */
    @PostMapping(DELIVERY_CREATE) // MODIFIED
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "')") // MODIFIED
    public Mono<Delivery> createDelivery(@Valid @RequestBody DeliveryRequest request) {
        // Basic input validation
        if (request.getOrderId() == null || request.getOrderId() <= 0
                || request.getRecipientName() == null || request.getRecipientName().isBlank()
                || request.getRecipientAddress() == null || request.getRecipientAddress().isBlank()
                || request.getDeliveryAgent() == null || request.getDeliveryAgent().isBlank()
                || request.getEstimatedDeliveryDate() == null) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DELIVERY_CREATION_REQUEST);
        }
        return deliveryService.createDelivery(
                request.getOrderId(),
                request.getRecipientName(),
                request.getRecipientAddress(),
                request.getDeliveryAgent(),
                request.getEstimatedDeliveryDate()
        );
        // Exceptions (InvalidDeliveryDataException, DeliveryNotFoundException, ResourceNotFoundException)
        // are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a delivery by its order ID.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the Delivery.
     * @throws IllegalArgumentException if order ID is invalid.
     * @throws DeliveryNotFoundException if the delivery is not found.
     */
    @GetMapping(DELIVERY_GET_BY_ORDER_ID) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Delivery> getDeliveryByOrderId(@PathVariable Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ORDER_ID);
        }
        return deliveryService.getDeliveryByOrderId(orderId);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a delivery by its tracking number.
     *
     * @param trackingNumber The tracking number.
     * @return A Mono emitting the Delivery.
     * @throws IllegalArgumentException if tracking number is invalid.
     * @throws DeliveryNotFoundException if the delivery is not found.
     */
    @GetMapping(DELIVERY_GET_BY_TRACKING_NUMBER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Delivery> getDeliveryByTrackingNumber(@PathVariable String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_TRACKING_NUMBER);
        }
        return deliveryService.getDeliveryByTrackingNumber(trackingNumber);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update the status of an existing delivery.
     *
     * @param request The DeliveryUpdateRequest DTO containing tracking number, new status, etc.
     * @return A Mono emitting the updated Delivery.
     * @throws IllegalArgumentException if input validation fails or new status is invalid.
     * @throws DeliveryNotFoundException if the delivery is not found.
     * @throws InvalidDeliveryDataException if status transition is not allowed.
     */
    @PutMapping(DELIVERY_UPDATE_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK) // Or HttpStatus.ACCEPTED if status update is async
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Mono<Delivery> updateDeliveryStatus(@Valid @RequestBody DeliveryUpdateRequest request) {
        if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()
                || request.getNewStatus() == null) { // Enum check is in service
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DELIVERY_STATUS_UPDATE_REQUEST);
        }
        return deliveryService.updateDeliveryStatus(
                request.getTrackingNumber(),
                request.getNewStatus().name(), // Pass status as String
                request.getCurrentLocation(),
                request.getNotes()
        );
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to cancel a delivery.
     *
     * @param trackingNumber The tracking number of the delivery to cancel.
     * @param reason Optional reason for cancellation.
     * @return A Mono emitting the updated Delivery.
     * @throws IllegalArgumentException if tracking number is invalid.
     * @throws DeliveryNotFoundException if the delivery is not found.
     * @throws InvalidDeliveryDataException if the delivery cannot be cancelled in its current state.
     */
    @PutMapping(DELIVERY_CANCEL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "')") // MODIFIED
    public Mono<Delivery> cancelDelivery(
            @PathVariable String trackingNumber,
            @RequestParam(required = false) String reason) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_TRACKING_NUMBER);
        }
        return deliveryService.cancelDelivery(trackingNumber, reason);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a delivery by tracking number.
     *
     * @param trackingNumber The tracking number of the delivery to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if tracking number is invalid.
     * @throws DeliveryNotFoundException if the delivery is not found.
     */
    @DeleteMapping(DELIVERY_ADMIN_DELETE) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Mono<Void> deleteDelivery(@PathVariable String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_TRACKING_NUMBER);
        }
        return deliveryService.deleteDelivery(trackingNumber);
        // Exceptions are handled by GlobalExceptionHandler.
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
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    @GetMapping(DELIVERY_ADMIN_GET_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Flux<Delivery> getAllDeliveriesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findAllDeliveries(pageable);
        // Errors are handled by GlobalExceptionHandler.
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
     * @throws IllegalArgumentException if status or pagination parameters are invalid.
     */
    @GetMapping(DELIVERY_ADMIN_GET_BY_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Flux<Delivery> getDeliveriesByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (status == null || status.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        DeliveryStatus deliveryStatus;
        try {
            deliveryStatus = DeliveryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DELIVERY_STATUS + status);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findDeliveriesByStatus(deliveryStatus, pageable);
        // Errors are handled by GlobalExceptionHandler.
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
     * @throws IllegalArgumentException if agent name or pagination parameters are invalid.
     */
    @GetMapping(DELIVERY_ADMIN_GET_BY_AGENT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Flux<Delivery> getDeliveriesByDeliveryAgent(
            @PathVariable String deliveryAgent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (deliveryAgent == null || deliveryAgent.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findDeliveriesByDeliveryAgent(deliveryAgent, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find deliveries with an estimated delivery date before a
     * specific date, with pagination.
     *
     * @param date The cutoff date (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Delivery records.
     * @throws IllegalArgumentException if date format or pagination parameters are invalid.
     */
    @GetMapping(DELIVERY_ADMIN_GET_ESTIMATED_BEFORE) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Flux<Delivery> getDeliveriesByEstimatedDeliveryDateBefore(
            @RequestParam String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (date == null || date.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findDeliveriesByEstimatedDeliveryDateBefore(dateTime, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find deliveries whose current location contains the specified
     * string (case-insensitive), with pagination.
     *
     * @param location The location string to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Delivery records.
     * @throws IllegalArgumentException if location or pagination parameters are invalid.
     */
    @GetMapping(DELIVERY_ADMIN_GET_BY_LOCATION) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Flux<Delivery> getDeliveriesByCurrentLocationContaining(
            @RequestParam String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (location == null || location.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return deliveryService.findDeliveriesByCurrentLocationContaining(location, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all delivery records.
     *
     * @return A Mono emitting the total count.
     */
    @GetMapping(DELIVERY_COUNT_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Mono<Long> countAllDeliveries() {
        return deliveryService.countAllDeliveries();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count deliveries for a specific order ID.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if order ID is invalid.
     */
    @GetMapping(DELIVERY_COUNT_BY_ORDER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_SELLER + "')") // MODIFIED
    public Mono<Long> countDeliveriesByOrderId(@PathVariable Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ORDER_ID);
        }
        return deliveryService.countDeliveriesByOrderId(orderId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count deliveries by their status.
     *
     * @param status The delivery status.
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if status is invalid.
     */
    @GetMapping(DELIVERY_COUNT_BY_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Mono<Long> countDeliveriesByStatus(@PathVariable String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DELIVERY_STATUS);
        }
        DeliveryStatus deliveryStatus;
        try {
            deliveryStatus = DeliveryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DELIVERY_STATUS + status);
        }
        return deliveryService.countDeliveriesByStatus(deliveryStatus);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count deliveries by the delivery agent.
     *
     * @param deliveryAgent The name of the delivery agent.
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if agent name is invalid.
     */
    @GetMapping(DELIVERY_COUNT_BY_AGENT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_DELIVERY_AGENT + "')") // MODIFIED
    public Mono<Long> countDeliveriesByAgent(@PathVariable String deliveryAgent) {
        if (deliveryAgent == null || deliveryAgent.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DELIVERY_AGENT);
        }
        return deliveryService.countDeliveriesByAgent(deliveryAgent);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count deliveries with an estimated delivery date before a
     * specific date.
     *
     * @param date The cutoff date (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if date format is invalid.
     */
    @GetMapping(DELIVERY_COUNT_ESTIMATED_BEFORE) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Mono<Long> countDeliveriesByEstimatedDeliveryDateBefore(@RequestParam String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DATE_FORMAT);
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(date);
            return deliveryService.countDeliveriesByEstimatedDeliveryDateBefore(dateTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");
        }
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count deliveries whose current location contains the
     * specified string (case-insensitive).
     *
     * @param location The location string to search for.
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if location is invalid.
     */
    @GetMapping(DELIVERY_COUNT_BY_LOCATION) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Mono<Long> countDeliveriesByCurrentLocationContaining(@RequestParam String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_LOCATION);
        }
        return deliveryService.countDeliveriesByCurrentLocationContaining(location);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to check if a delivery record exists for a given tracking
     * number.
     *
     * @param trackingNumber The tracking number.
     * @return A Mono emitting true if it exists, false otherwise (Boolean).
     * @throws IllegalArgumentException if tracking number is invalid.
     */
    @GetMapping(DELIVERY_EXISTS_BY_TRACKING_NUMBER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsByTrackingNumber(@PathVariable String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_TRACKING_NUMBER);
        }
        return deliveryService.existsByTrackingNumber(trackingNumber);
        // Errors are handled by GlobalExceptionHandler.
    }
}