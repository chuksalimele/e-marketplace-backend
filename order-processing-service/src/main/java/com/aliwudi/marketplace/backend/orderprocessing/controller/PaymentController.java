package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.orderprocessing.dto.PaymentRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.PaymentResponse;
import com.aliwudi.marketplace.backend.orderprocessing.model.Payment;
import com.aliwudi.marketplace.backend.orderprocessing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public Mono<ResponseEntity<PaymentResponse>> initiatePayment(@RequestBody PaymentRequest request) {
        // In a real application, before initiating payment, you would:
        // 1. Create the order record in your OrderService.
        // 2. Reserve stock in InventoryService for the items in the order.
        // 3. Then, initiate payment for that order.
        // For this example, we're directly initiating payment and passing product ID for inventory.

        return paymentService.initiatePayment(
                request.getOrderId(),
                request.getAmount(),
                request.getProductIdForInventory() // Temporary: in real use, would get this from order
            )
            .map(payment -> ResponseEntity.status(HttpStatus.CREATED).body(
                PaymentResponse.builder()
                    .orderId(payment.getOrderId())
                    .transactionRef(payment.getTransactionRef())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .paymentDate(payment.getPaymentDate())
                    .message("Payment initiated. Awaiting gateway confirmation.")
                    .build()
            ));
            // You might add onErrorResume here for specific service-level exceptions
    }

    // This endpoint would typically be used by the payment gateway as a webhook
    // It's simplified for demonstration. Real webhooks have security measures.
    @PostMapping("/webhook/{transactionRef}")
    public Mono<ResponseEntity<String>> handleGatewayCallback(@PathVariable String transactionRef,
                                                             @RequestParam("status") String status,
                                                             @RequestParam("orderId") String orderId,
                                                             @RequestParam("productId") String productId, // For inventory
                                                             @RequestParam("quantity") Integer quantity) { // For inventory
        return Mono.just(status.toUpperCase()) // Ensure status is uppercase for enum conversion
                .map(Payment.PaymentStatus::valueOf) // Convert String to PaymentStatus enum
                .onErrorResume(IllegalArgumentException.class, e -> // Handle invalid enum value
                    Mono.just(ResponseEntity.badRequest().body("Invalid payment status: " + e.getMessage())))
                .flatMap(paymentStatus ->
                    paymentService.processGatewayCallback(transactionRef, paymentStatus, "Webhook Callback Data", orderId, productId, quantity)
                        .then(Mono.just(ResponseEntity.ok("Callback processed successfully"))) // After completion, return success
                )
                .onErrorResume(Exception.class, e -> // Catch any other unexpected exceptions
                    Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing callback: " + e.getMessage())));
    }

    @GetMapping("/{orderId}")
    public Mono<ResponseEntity<PaymentResponse>> getPaymentStatus(@PathVariable String orderId) {
        return paymentService.getPaymentDetails(orderId) // Service returns Mono<Payment>
            .map(payment -> ResponseEntity.ok(
                PaymentResponse.builder()
                    .orderId(payment.getOrderId())
                    .transactionRef(payment.getTransactionRef())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .paymentDate(payment.getPaymentDate())
                    .message("Payment status fetched.")
                    .build()
            ))
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build())); // Handle case where payment details are not found
    }
}