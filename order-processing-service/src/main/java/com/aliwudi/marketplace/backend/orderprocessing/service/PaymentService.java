package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.status.PaymentStatus;
import com.aliwudi.marketplace.backend.orderprocessing.model.Payment;
import com.aliwudi.marketplace.backend.orderprocessing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Keep for reactive transaction management
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService; // To interact with inventory after payment

    @Transactional
    public Mono<Payment> initiatePayment(String orderId, BigDecimal amount) {
        log.info("Initiating payment for orderId: {}, amount: {}", orderId, amount);

        // 1. Create a pending payment record
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .transactionRef(UUID.randomUUID().toString()) // Generate a unique transaction reference
                .status(PaymentStatus.PENDING)
                .paymentDate(LocalDateTime.now())
                .gatewayResponse("SIMULATED_INITIATED")
                .build();

        return paymentRepository.save(payment) // Save the payment record
                .doOnSuccess(savedPayment -> {
                    log.info("Payment initiated and marked as PENDING. TransactionRef: {}", savedPayment.getTransactionRef());

                    // In a real scenario, here you would call the external payment gateway API.
                    // The gateway would typically return a redirect URL or a token for client-side payment.
                    // For this example, we'll simulate success directly.
                    // The 'then' operator ensures this simulation runs after the payment is saved.
                })
                .flatMap(savedPayment ->
                    // Simulate a successful payment immediately for demonstration
                    // In a real app, this would be handled by a webhook/callback from the gateway
                    // The call to processGatewayCallback should also be reactive.
                    processGatewayCallback(savedPayment.getTransactionRef(), PaymentStatus.SUCCESS, "SIMULATED_SUCCESS", orderId, amount.intValue())
                        .thenReturn(savedPayment) // Return the original savedPayment after callback processing
                );
    }

    // This method would typically be called by a webhook from the payment gateway
    @Transactional
    public Mono<Payment> processGatewayCallback(String transactionRef, PaymentStatus newStatus, String gatewayResponse, String orderId, int quantityConfirmed) {
        log.info("Processing payment gateway callback for transactionRef: {} with status: {}", transactionRef, newStatus);

        return paymentRepository.findByTransactionRef(transactionRef) // Returns Mono<Payment>
                .switchIfEmpty(Mono.error(new RuntimeException("Payment record not found for transactionRef: " + transactionRef)))
                .flatMap(payment -> {
                    if (payment.getStatus().equals(PaymentStatus.PENDING)) {
                        payment.setStatus(newStatus);
                        payment.setGatewayResponse(gatewayResponse);
                        payment.setPaymentDate(LocalDateTime.now()); // Update payment date to confirmation time

                        return paymentRepository.save(payment) // Save the updated payment
                                .flatMap(savedPayment -> {
                                    Mono<Void> inventoryOperation = Mono.empty(); // Initialize with an empty Mono

                                    if (newStatus.equals(PaymentStatus.SUCCESS)) {
                                        log.info("Payment SUCCESS for orderId: {}. Deducting stock.", savedPayment.getOrderId());
                                        // Crucial: Deduct reserved stock upon successful payment
                                        inventoryOperation = inventoryService.confirmReservationAndDeductStock(orderId, quantityConfirmed);
                                        // After successful payment, update order status (e.g., to 'PAID' or 'PROCESSING')
                                        // This would involve calling an OrderService method: orderService.updateOrderStatus(payment.getOrderId(), OrderStatus.PAID);
                                    } else if (newStatus.equals(PaymentStatus.FAILED) || newStatus.equals(PaymentStatus.REFUNDED)) {
                                        log.warn("Payment FAILED/REFUNDED for orderId: {}. Releasing reserved stock.", savedPayment.getOrderId());
                                        // Release reserved stock if payment fails
                                        inventoryOperation = inventoryService.releaseStock(orderId, quantityConfirmed);
                                        // Update order status to 'PAYMENT_FAILED' or 'CANCELLED'
                                        // orderService.updateOrderStatus(payment.getOrderId(), OrderStatus.PAYMENT_FAILED);
                                    }
                                    // Execute the inventory operation and then return the saved payment
                                    return inventoryOperation.thenReturn(savedPayment);
                                });
                    } else {
                        log.warn("Payment for transactionRef: {} already processed with status: {}", transactionRef, payment.getStatus());
                        return Mono.just(payment); // Return the existing payment record
                    }
                });
    }

    @Transactional(readOnly = true)
    public Mono<Payment> getPaymentDetails(String orderId) {
        log.info("Fetching payment details for orderId: {}", orderId);
        return paymentRepository.findByOrderId(orderId) // Returns Mono<Payment>
                .switchIfEmpty(Mono.error(new RuntimeException("Payment not found for orderId: " + orderId)));
    }
}