package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.dto.PaymentDto;
import com.aliwudi.marketplace.backend.orderprocessing.dto.PaymentRequest;
import com.aliwudi.marketplace.backend.orderprocessing.model.Payment; // Import Payment model
import com.aliwudi.marketplace.backend.orderprocessing.service.PaymentService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux for methods returning multiple items
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.status.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;


@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public Mono<StandardResponseEntity> initiatePayment(@RequestBody PaymentRequest request) {
        if (request.getOrderId() == null || request.getAmount() == null || request.getAmount().doubleValue() <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAYMENT_INITIATION_REQUEST));
        }

        return paymentService.initiatePayment(
                request.getOrderId(),
                request.getAmount()
            )
            .map(payment -> (StandardResponseEntity) StandardResponseEntity.created(PaymentDto.builder()
                    .order(payment.getOrderId())
                    .transactionRef(payment.getTransactionRef())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .paymentDate(payment.getPaymentDate())
                    .build(),
                ApiResponseMessages.PAYMENT_INITIATED_SUCCESS
            ))
            .onErrorResume(ResourceNotFoundException.class, e ->
                Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + request.getOrderId())))
            .onErrorResume(InsufficientStockException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
            .onErrorResume(Exception.class, e ->
                Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_INITIATING_PAYMENT + ": " + e.getMessage())));
    }

    @PostMapping("/webhook/{transactionRef}")
    public Mono<StandardResponseEntity> handleGatewayCallback(@PathVariable String transactionRef,
                                                              @RequestParam("status") String status,
                                                              @RequestParam("orderId") String orderId,
                                                              @RequestParam(value = "productId", required = false) String productId,
                                                              @RequestParam(value = "quantity", required = false) Integer quantity) {

        if (status == null || status.trim().isEmpty() || orderId == null || orderId.trim().isEmpty()) {
             return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_WEBHOOK_CALLBACK_REQUEST));
        }

        return Mono.just(status.toUpperCase())
                .map(PaymentStatus::valueOf)
                .flatMap(paymentStatus ->
                    // Pass orderId and quantity to processGatewayCallback
                    paymentService.processGatewayCallback(transactionRef, paymentStatus, "Webhook Callback Data", orderId, quantity != null ? quantity : 0)
                        .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.PAYMENT_CALLBACK_PROCESSED_SUCCESS)))
                )
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAYMENT_STATUS_VALUE + status)))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PAYMENT_NOT_FOUND + transactionRef)))
                .onErrorResume(Exception.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_PROCESSING_CALLBACK + ": " + e.getMessage())));
    }

    @GetMapping("/{orderId}")
    public Mono<StandardResponseEntity> getPaymentStatus(@PathVariable String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.MISSING_ORDER_ID_FOR_PAYMENT_STATUS));
        }

        return paymentService.getPaymentDetails(orderId)
            .map(payment -> (StandardResponseEntity) StandardResponseEntity.ok(PaymentDto.builder()
                    .order(payment.getOrderId())
                    .transactionRef(payment.getTransactionRef())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .paymentDate(payment.getPaymentDate())
                    .build(),
                ApiResponseMessages.PAYMENT_STATUS_FETCHED_SUCCESS
            ))
            .onErrorResume(ResourceNotFoundException.class, e ->
                Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(NumberFormatException.class, e ->
                    Mono.just(StandardResponseEntity.badRequest("Invalid Order ID format: " + orderId)))
            .onErrorResume(Exception.class, e ->
                Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_FETCHING_PAYMENT_STATUS + ": " + e.getMessage())));
    }

    // --- NEW: PaymentRepository Controller Endpoints ---

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
    public Flux<Payment> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return paymentService.findAllPayments(pageable);
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
    public Flux<Payment> getPaymentsByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return paymentService.findPaymentsByUserId(userId, pageable)
                .onErrorResume(NumberFormatException.class, e ->
                        Flux.error(new IllegalArgumentException("Invalid User ID format: " + userId)));
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
     */
    @GetMapping("/admin/byStatus/{status}")
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
            return Flux.error(new IllegalArgumentException("Invalid payment status: " + status));
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return paymentService.findPaymentsByStatus(paymentStatus, pageable);
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
     */
    @GetMapping("/admin/byTimeRange")
    public Flux<Payment> getPaymentsByPaymentTimeBetween(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            return paymentService.findPaymentsByPaymentTimeBetween(start, end, pageable);
        } catch (DateTimeParseException e) {
            return Flux.error(new IllegalArgumentException("Invalid date format. Please use ISO 8601 format: YYYY-MM-ddTHH:mm:ss."));
        }
    }

    /**
     * Endpoint to find a payment by its unique transaction reference.
     *
     * @param transactionRef The unique transaction reference.
     * @return A Mono emitting StandardResponseEntity with the Payment record.
     */
    @GetMapping("/byTransactionRef/{transactionRef}")
    public Mono<StandardResponseEntity> getPaymentByTransactionRef(@PathVariable String transactionRef) {
        return paymentService.findPaymentByTransactionRef(transactionRef)
                .map(payment -> StandardResponseEntity.ok(payment, "Payment retrieved by transaction reference."))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound("Payment not found for transaction reference: " + transactionRef)))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all payments.
     *
     * @return A Mono emitting StandardResponseEntity with the total count.
     */
    @GetMapping("/count/all")
    public Mono<StandardResponseEntity> countAllPayments() {
        return paymentService.countAllPayments()
                .map(count -> StandardResponseEntity.ok(count, "Total payments counted."))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count payments made by a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byUser/{userId}")
    public Mono<StandardResponseEntity> countPaymentsByUserId(@PathVariable String userId) {
        return paymentService.countPaymentsByUserId(userId)
                .map(count -> StandardResponseEntity.ok(count, "Payments for user " + userId + " counted."))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid User ID format: " + userId)))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count payments by their status.
     *
     * @param status The payment status.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byStatus/{status}")
    public Mono<StandardResponseEntity> countPaymentsByStatus(@PathVariable String status) {
        PaymentStatus paymentStatus;
        try {
            paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(StandardResponseEntity.badRequest("Invalid payment status: " + status));
        }
        return paymentService.countPaymentsByStatus(paymentStatus)
                .map(count -> StandardResponseEntity.ok(count, "Payments with status " + status + " counted."))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count payments made within a specific time range.
     *
     * @param startTime The start time of the range (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @param endTime The end time of the range (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byTimeRange")
    public Mono<StandardResponseEntity> countPaymentsByPaymentTimeBetween(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            return paymentService.countPaymentsByPaymentTimeBetween(start, end)
                    .map(count -> StandardResponseEntity.ok(count, "Payments between " + startTime + " and " + endTime + " counted."))
                    .onErrorResume(Exception.class, e ->
                            Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
        } catch (DateTimeParseException e) {
            return Mono.just(StandardResponseEntity.badRequest("Invalid date format. Please use ISO 8601 format: YYYY-MM-ddTHH:mm:ss."));
        }
    }

    /**
     * Endpoint to check if a payment with a given transaction reference exists.
     *
     * @param transactionRef The unique transaction reference.
     * @return A Mono emitting StandardResponseEntity with a boolean indicating existence.
     */
    @GetMapping("/exists/byTransactionRef/{transactionRef}")
    public Mono<StandardResponseEntity> existsPaymentByTransactionRef(@PathVariable String transactionRef) {
        return paymentService.existsPaymentByTransactionRef(transactionRef)
                .map(exists -> StandardResponseEntity.ok(exists, "Payment existence check for transaction reference " + transactionRef + " completed."))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }
}