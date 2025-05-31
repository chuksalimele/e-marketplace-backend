package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.orderprocessing.dto.PaymentRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.PaymentResponse;
import com.aliwudi.marketplace.backend.orderprocessing.model.Payment;
import com.aliwudi.marketplace.backend.orderprocessing.service.PaymentService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException; // Assuming this for payment/order not found
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException; // If payment initiation involves stock check

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public Mono<StandardResponseEntity> initiatePayment(@RequestBody PaymentRequest request) {
        // In a real application, before initiating payment, you would:
        // 1. Create the order record in your OrderService.
        // 2. Reserve stock in InventoryService for the items in the order.
        // 3. Then, initiate payment for that order.
        // For this example, we're directly initiating payment and passing product ID for inventory.

        if (request.getOrderId() == null || request.getAmount() == null || request.getAmount().doubleValue() <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAYMENT_INITIATION_REQUEST));
        }

        return paymentService.initiatePayment(
                request.getOrderId(),
                request.getAmount(),
                request.getProductIdForInventory() // Temporary: in real use, would get this from order
            )
            .map(payment -> (StandardResponseEntity) StandardResponseEntity.created(PaymentResponse.builder()
                    .orderId(payment.getOrderId())
                    .transactionRef(payment.getTransactionRef())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .paymentDate(payment.getPaymentDate())
                    .build(),
                ApiResponseMessages.PAYMENT_INITIATED_SUCCESS // Specific message for initiation
            ))
            // Error handling for initiation process
            .onErrorResume(ResourceNotFoundException.class, e ->
                Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + request.getOrderId())))
            .onErrorResume(InsufficientStockException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
            .onErrorResume(Exception.class, e ->
                Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_INITIATING_PAYMENT + ": " + e.getMessage())));
    }

    // This endpoint would typically be used by the payment gateway as a webhook
    // It's simplified for demonstration. Real webhooks have security measures.
    @PostMapping("/webhook/{transactionRef}")
    public Mono<StandardResponseEntity> handleGatewayCallback(@PathVariable String transactionRef,
                                                              @RequestParam("status") String status,
                                                              @RequestParam("orderId") String orderId,
                                                              @RequestParam(value = "productId", required = false) String productId, // Made optional for cases where it's not needed by webhook
                                                              @RequestParam(value = "quantity", required = false) Integer quantity) { // Made optional

        if (status == null || status.trim().isEmpty() || orderId == null || orderId.trim().isEmpty()) {
             return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_WEBHOOK_CALLBACK_REQUEST));
        }

        return Mono.just(status.toUpperCase())
                .map(Payment.PaymentStatus::valueOf) // Convert status string to enum
                .flatMap(paymentStatus ->
                    paymentService.processGatewayCallback(transactionRef, paymentStatus, "Webhook Callback Data", orderId, productId, quantity)
                        .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.PAYMENT_CALLBACK_PROCESSED_SUCCESS)))
                )
                // Handle exceptions from both the enum conversion and the service call
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAYMENT_STATUS_VALUE + status)))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PAYMENT_NOT_FOUND + transactionRef)))
                .onErrorResume(Exception.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_PROCESSING_CALLBACK + ": " + e.getMessage())));
    }

    @GetMapping("/{orderId}")
    public Mono<StandardResponseEntity> getPaymentStatus(@PathVariable String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.MISSING_ORDER_ID_FOR_PAYMENT_STATUS));
        }

        return paymentService.getPaymentDetails(orderId) // Service returns Mono<Payment>
            .map(payment -> (StandardResponseEntity) StandardResponseEntity.ok(PaymentResponse.builder()
                    .orderId(payment.getOrderId())
                    .transactionRef(payment.getTransactionRef())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .paymentDate(payment.getPaymentDate())
                    .build(),
                ApiResponseMessages.PAYMENT_STATUS_FETCHED_SUCCESS
            ))
            .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PAYMENT_NOT_FOUND_FOR_ORDER + orderId))) // Explicitly throw if not found
            .onErrorResume(ResourceNotFoundException.class, e ->
                Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage()))) // Catch and map
            .onErrorResume(Exception.class, e ->
                Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_FETCHING_PAYMENT_STATUS + ": " + e.getMessage())));
    }
}