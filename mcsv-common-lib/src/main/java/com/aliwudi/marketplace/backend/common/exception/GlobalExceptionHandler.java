// GlobalExceptionHandler.java
package com.aliwudi.marketplace.backend.common.exception; // Note: This package implies a common module for exceptions and handler

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j; // Added for logging functionality

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects; // Added for Objects.requireNonNull in validation handler

@RestControllerAdvice // Combines @ControllerAdvice and @ResponseBody
@Slf4j // Enables Lombok's logging, used throughout the class
public class GlobalExceptionHandler {

    /**
     * Helper record for consistent error response structure.
     * Includes timestamp, a descriptive message, and a details/error code.
     */
    private record ErrorDetails(LocalDateTime timestamp, String message, String details) {}

    /**
     * Handles validation errors, specifically for @Valid annotated request bodies.
     * Catches `WebExchangeBindException` which contains details about binding and validation failures.
     * Returns HTTP status 400 Bad Request with a map of field errors.
     *
     * @param ex The WebExchangeBindException instance.
     * @return A Mono emitting ResponseEntity with a map of field errors.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleValidationExceptions(WebExchangeBindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String fieldName = error.getField();
            String errorMessage = Objects.requireNonNull(error.getDefaultMessage()); // Ensure message is not null
            errors.put(fieldName, errorMessage);
            log.warn("Validation error in field '{}': {}", fieldName, errorMessage); // Log specific validation errors
        });
        log.error("Validation error summary: {}", errors); // Log the overall validation error summary
        return Mono.just(ResponseEntity.badRequest().body(errors));
    }

    /**
     * Handles `ResourceNotFoundException` (custom generic not found exception).
     * Returns HTTP status 404 Not Found.
     *
     * @param ex The ResourceNotFoundException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "RESOURCE_NOT_FOUND"
        );
        log.warn("Resource not found: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND));
    }

    /**
     * Handles `DeliveryNotFoundException`.
     * Returns HTTP status 404 Not Found.
     *
     * @param ex The DeliveryNotFoundException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(DeliveryNotFoundException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleDeliveryNotFoundException(DeliveryNotFoundException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "DELIVERY_NOT_FOUND"
        );
        log.warn("Delivery not found: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND));
    }

    /**
     * Handles `InventoryNotFoundException`.
     * Returns HTTP status 404 Not Found.
     *
     * @param ex The InventoryNotFoundException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InventoryNotFoundException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInventoryNotFoundException(InventoryNotFoundException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INVENTORY_NOT_FOUND"
        );
        log.warn("Inventory not found: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND));
    }

    /**
     * Handles `MediaAssetNotFoundException`.
     * Returns HTTP status 404 Not Found.
     *
     * @param ex The MediaAssetNotFoundException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(MediaAssetNotFoundException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleMediaAssetNotFoundException(MediaAssetNotFoundException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "MEDIA_ASSET_NOT_FOUND"
        );
        log.warn("Media asset not found: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND));
    }

    /**
     * Handles `RoleNotFoundException`.
     * Returns HTTP status 404 Not Found.
     *
     * @param ex The RoleNotFoundException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(RoleNotFoundException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleRoleNotFoundException(RoleNotFoundException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "ROLE_NOT_FOUND"
        );
        log.warn("Role not found: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND));
    }

    /**
     * Handles `DataIntegrityViolationException` (e.g., unique constraint violation from R2DBC).
     * Returns HTTP status 409 Conflict.
     *
     * @param ex The DataIntegrityViolationException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Data integrity violation: " + ex.getMessage(),
                "DATABASE_CONFLICT"
        );
        log.error("Data integrity violation: {}", ex.getMessage(), ex);
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT));
    }

    /**
     * Handles `DuplicateResourceException` (custom exception for existing resources).
     * Returns HTTP status 409 Conflict.
     *
     * @param ex The DuplicateResourceException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleDuplicateResourceException(DuplicateResourceException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "DUPLICATE_RESOURCE"
        );
        log.warn("Duplicate resource detected: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT));
    }

    /**
     * Handles `IllegalArgumentException` (general invalid argument or business rule violation).
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The IllegalArgumentException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "BAD_REQUEST_ARGUMENT"
        );
        log.warn("Illegal argument provided: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `IllegalStateException` (application state issues, e.g., authentication context).
     * Returns HTTP status 401 Unauthorized, 403 Forbidden, or 500 Internal Server Error.
     *
     * @param ex The IllegalStateException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleIllegalStateException(IllegalStateException ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String details = "INTERNAL_SERVER_ERROR";

        // Attempt to categorize common IllegalStateExceptions based on message content
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("Unauthenticated") || ex.getMessage().contains("SECURITY_CONTEXT_NOT_FOUND")) {
                status = HttpStatus.UNAUTHORIZED;
                details = "UNAUTHORIZED_ACCESS";
            } else if (ex.getMessage().contains("Invalid principal") || ex.getMessage().contains("INVALID_USER_ID")) {
                status = HttpStatus.FORBIDDEN; // More specific than BAD_REQUEST for auth principal issues
                details = "FORBIDDEN_INVALID_PRINCIPAL";
            }
        }

        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                details
        );
        log.error("Illegal state encountered: {}", ex.getMessage(), ex);
        return Mono.just(new ResponseEntity<>(errorDetails, status));
    }

    /**
     * Handles `InsufficientStockException`.
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The InsufficientStockException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInsufficientStockException(InsufficientStockException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INSUFFICIENT_STOCK"
        );
        log.warn("Insufficient stock: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `InvalidDeliveryDataException`.
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The InvalidDeliveryDataException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InvalidDeliveryDataException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInvalidDeliveryDataException(InvalidDeliveryDataException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INVALID_DELIVERY_DATA"
        );
        log.warn("Invalid delivery data: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `InvalidMediaDataException`.
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The InvalidMediaDataException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InvalidMediaDataException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInvalidMediaDataException(InvalidMediaDataException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INVALID_MEDIA_DATA"
        );
        log.warn("Invalid media data: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `InvalidPasswordException`.
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The InvalidPasswordException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InvalidPasswordException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInvalidPasswordException(InvalidPasswordException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INVALID_PASSWORD"
        );
        log.warn("Invalid password: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `InvalidProductDataException`.
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The InvalidProductDataException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InvalidProductDataException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInvalidProductDataException(InvalidProductDataException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INVALID_PRODUCT_DATA"
        );
        log.warn("Invalid product data: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `InvalidReviewDataException`.
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The InvalidReviewDataException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InvalidReviewDataException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInvalidReviewDataException(InvalidReviewDataException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INVALID_REVIEW_DATA"
        );
        log.warn("Invalid review data: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `InvalidSellerDataException`.
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The InvalidSellerDataException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InvalidSellerDataException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInvalidSellerDataException(InvalidSellerDataException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INVALID_SELLER_DATA"
        );
        log.warn("Invalid seller data: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `InvalidStoreDataException`.
     * Returns HTTP status 400 Bad Request.
     *
     * @param ex The InvalidStoreDataException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(InvalidStoreDataException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleInvalidStoreDataException(InvalidStoreDataException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "INVALID_STORE_DATA"
        );
        log.warn("Invalid store data: {}", ex.getMessage());
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST));
    }

    /**
     * Handles `ServiceUnavailableException`.
     * Returns HTTP status 503 Service Unavailable.
     *
     * @param ex The ServiceUnavailableException instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public Mono<ResponseEntity<ErrorDetails>> handleServiceUnavailableException(ServiceUnavailableException ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                "SERVICE_UNAVAILABLE"
        );
        log.error("Dependent service unavailable: {}", ex.getMessage(), ex); // Log as error as this indicates an upstream issue
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.SERVICE_UNAVAILABLE));
    }

    /**
     * Handles all other unexpected exceptions not caught by more specific handlers.
     * This acts as a fallback to catch any unhandled runtime exceptions.
     * Returns HTTP status 500 Internal Server Error.
     *
     * @param ex The general Exception instance.
     * @return A Mono emitting ResponseEntity with structured error details.
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorDetails>> handleGlobalException(Exception ex) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "An unexpected error occurred: " + ex.getMessage(),
                "INTERNAL_SERVER_ERROR"
        );
        log.error("An unexpected error occurred: {}", ex.getMessage(), ex);
        return Mono.just(new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
