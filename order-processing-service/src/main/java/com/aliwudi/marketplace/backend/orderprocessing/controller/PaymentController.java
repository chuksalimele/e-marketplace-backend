package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.model.Payment;
import com.aliwudi.marketplace.backend.orderprocessing.dto.PaymentRequest;
import com.aliwudi.marketplace.backend.orderprocessing.service.PaymentService;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.status.PaymentStatus;

import jakarta.validation.Valid; // For @Valid annotation
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map; // Used for webhook/callback payload

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    // Removed direct injection of OrderService as it's used within PaymentService for prepareDto and status updates.

    /**
     * Endpoint to initiate a payment.
     *
     * @param request The PaymentRequest DTO containing orderId and amount.
     * @return A Mono emitting the created Payment.
     * @throws IllegalArgumentException if the request data is invalid.
     * @throws com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException if the order is not found.
     * @throws com.aliwudi.marketplace.backend.common.exception.InsufficientStockException if there's insufficient stock.
     */
    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    public Mono<Payment> initiatePayment(@Valid @RequestBody PaymentRequest request) {
        // Basic validation already done by @Valid and DTO constraints.
        // Additional business logic validation:
        if (request.getAmount() == null || request.getAmount().doubleValue() <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAYMENT_INITIATION_REQUEST);
        }
        return paymentService.initiatePayment(request.getOrderId(), request.getAmount());
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to handle payment gateway callbacks (webhooks).
     * This endpoint updates the payment status and triggers inventory management.
     *
     * @param transactionRef The unique transaction reference from the gateway.
     * @param status The payment status string from the gateway (e.g., "success", "failed").
     * @param orderId The ID of the order associated with the payment.
     * @param quantity The quantity of the product for inventory operations (optional, often derived from order items).
     * @return A Mono<Void> indicating successful processing (HTTP 204 No Content).
     * @throws IllegalArgumentException if the status string is invalid or request data is missing.
     * @throws com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException if the payment record is not found.
     */
    @PostMapping("/webhook/{transactionRef}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content for successful processing
    public Mono<Void> handleGatewayCallback(
            @PathVariable String transactionRef,
            @RequestParam("status") String status,
            @RequestParam("orderId") Long orderId,
            @RequestParam(value = "quantity", required = false, defaultValue = "0") Integer quantity) { // Default to 0 for safety

        if (status == null || status.trim().isEmpty() || orderId == null) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_WEBHOOK_CALLBACK_REQUEST);
        }

        PaymentStatus paymentStatus;
        try {
            paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAYMENT_STATUS_VALUE + status);
        }

        return paymentService.processGatewayCallback(transactionRef, paymentStatus, "Webhook Callback Data", orderId, quantity)
                .then(); // Return Mono<Void> for 204 No Content
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to get payment status and details for a given order ID.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the Payment details.
     * @throws IllegalArgumentException if orderId is invalid.
     * @throws com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException if payment for the order is not found.
     */
    @GetMapping("/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Payment> getPaymentStatus(@PathVariable Long orderId) {
        if (orderId == null || orderId <= 0) { // Add check for non-positive IDs
            throw new IllegalArgumentException(ApiResponseMessages.MISSING_ORDER_ID_FOR_PAYMENT_STATUS);
        }
        return paymentService.getPaymentDetails(orderId);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    // --- PaymentRepository Controller Endpoints ---

    /**
     * Endpoint to retrieve all payments with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Payment records.
     */
    @GetMapping("/admin/all")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Payment> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return paymentService.findAllPayments(pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find payments made by a specific user with pagination.
     *
     * @param userId The ID of the user.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Payment records for the specified user.
     */
    @GetMapping("/admin/byUser/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Payment> getPaymentsByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return paymentService.findPaymentsByUserId(userId, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find payments by their status with pagination.
     *
     * @param status The payment status (e.g., "PENDING", "SUCCESS", "FAILED").
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Payment records with the specified status.
     * @throws IllegalArgumentException if the status string is invalid.
     */
    @GetMapping("/admin/byStatus/{status}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Payment> getPaymentsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        PaymentStatus paymentStatus;
        try {
            paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid payment status: " + status);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return paymentService.findPaymentsByStatus(paymentStatus, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find payments made within a specific time range with pagination.
     *
     * @param startTime The start of the time range (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @param endTime The end of the time range (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Payment records within the specified time range.
     * @throws IllegalArgumentException if the date format is invalid.
     */
    @GetMapping("/admin/byTimeRange")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Payment> getPaymentsByPaymentTimeBetween(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        LocalDateTime start;
        LocalDateTime end;

        try {
            start = LocalDateTime.parse(startTime);
            end = LocalDateTime.parse(endTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");
        }

        return paymentService.findPaymentsByPaymentTimeBetween(start, end, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find a payment by its unique transaction reference.
     *
     * @param transactionRef The unique transaction reference.
     * @return A Mono emitting the Payment record.
     * @throws com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException if the payment is not found.
     */
    @GetMapping("/byTransactionRef/{transactionRef}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Payment> getPaymentByTransactionRef(@PathVariable String transactionRef) {
        return paymentService.findPaymentByTransactionRef(transactionRef);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all payments.
     *
     * @return A Mono emitting the total count (Long).
     */
    @GetMapping("/count/all")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllPayments() {
        return paymentService.countAllPayments();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count payments made by a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the count (Long).
     */
    @GetMapping("/count/byUser/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countPaymentsByUserId(@PathVariable Long userId) {
        // userId was String in original, but methods now take Long in service.
        // Assuming path variable will be parsed to Long directly.
        return paymentService.countPaymentsByUserId(userId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count payments by their status.
     *
     * @param status The payment status.
     * @return A Mono emitting the count (Long).
     * @throws IllegalArgumentException if the status string is invalid.
     */
    @GetMapping("/count/byStatus/{status}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countPaymentsByStatus(@PathVariable String status) {
        PaymentStatus paymentStatus;
        try {
            paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid payment status: " + status);
        }
        return paymentService.countPaymentsByStatus(paymentStatus);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count payments made within a specific time range.
     *
     * @param startTime The start time of the range (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @param endTime The end time of the range (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @return A Mono emitting the count (Long).
     * @throws IllegalArgumentException if the date format is invalid.
     */
    @GetMapping("/count/byTimeRange")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countPaymentsByPaymentTimeBetween(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            return paymentService.countPaymentsByPaymentTimeBetween(start, end);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");
        }
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to check if a payment with a given transaction reference exists.
     *
     * @param transactionRef The unique transaction reference.
     * @return A Mono emitting true if it exists, false otherwise (Boolean).
     */
    @GetMapping("/exists/byTransactionRef/{transactionRef}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsPaymentByTransactionRef(@PathVariable String transactionRef) {
        return paymentService.existsPaymentByTransactionRef(transactionRef);
        // Errors are handled by GlobalExceptionHandler.
    }
}
