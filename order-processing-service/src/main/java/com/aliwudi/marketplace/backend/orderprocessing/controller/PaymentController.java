package com.aliwudi.marketplace.backend.orderprocessing.controller;


import com.aliwudi.marketplace.backend.orderprocessing.dto.PaymentRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.PaymentResponse;
import com.aliwudi.marketplace.backend.orderprocessing.model.Payment;
import com.aliwudi.marketplace.backend.orderprocessing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiatePayment(@RequestBody PaymentRequest request) {
        // In a real application, before initiating payment, you would:
        // 1. Create the order record in your OrderService.
        // 2. Reserve stock in InventoryService for the items in the order.
        // 3. Then, initiate payment for that order.
        // For this example, we're directly initiating payment and passing product ID for inventory.

        Payment payment = paymentService.initiatePayment(
            request.getOrderId(),
            request.getAmount(),
            request.getProductIdForInventory() // Temporary: in real use, would get this from order
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            PaymentResponse.builder()
                .orderId(payment.getOrderId())
                .transactionRef(payment.getTransactionRef())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentDate(payment.getPaymentDate())
                .message("Payment initiated. Awaiting gateway confirmation.")
                .build()
        );
    }

    // This endpoint would typically be used by the payment gateway as a webhook
    // It's simplified for demonstration. Real webhooks have security measures.
    @PostMapping("/webhook/{transactionRef}")
    public ResponseEntity<String> handleGatewayCallback(@PathVariable String transactionRef,
                                                       @RequestParam("status") String status,
                                                       @RequestParam("orderId") String orderId,
                                                       @RequestParam("productId") String productId, // For inventory
                                                       @RequestParam("quantity") Integer quantity) { // For inventory
        try {
            Payment.PaymentStatus paymentStatus = Payment.PaymentStatus.valueOf(status.toUpperCase());
            paymentService.processGatewayCallback(transactionRef, paymentStatus, "Webhook Callback Data", orderId, productId, quantity);
            return ResponseEntity.ok("Callback processed successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid payment status or parameters: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing callback: " + e.getMessage());
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String orderId) {
        Payment payment = paymentService.getPaymentDetails(orderId);
        return ResponseEntity.ok(
            PaymentResponse.builder()
                .orderId(payment.getOrderId())
                .transactionRef(payment.getTransactionRef())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .paymentDate(payment.getPaymentDate())
                .message("Payment status fetched.")
                .build()
        );
    }
}