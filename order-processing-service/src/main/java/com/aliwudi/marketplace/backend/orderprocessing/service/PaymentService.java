package com.aliwudi.marketplace.backend.orderprocessing.service;


import com.aliwudi.marketplace.backend.orderprocessing.model.Payment;
import com.aliwudi.marketplace.backend.orderprocessing.model.Payment.PaymentStatus;
import com.aliwudi.marketplace.backend.orderprocessing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Payment initiatePayment(String orderId, BigDecimal amount, String productIdForInventory) {
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
        payment = paymentRepository.save(payment);

        log.info("Payment initiated and marked as PENDING. TransactionRef: {}", payment.getTransactionRef());

        // In a real scenario, here you would call the external payment gateway API
        // e.g., Paystack, Flutterwave, etc.
        // The gateway would typically return a redirect URL or a token for client-side payment.
        // For this example, we'll just simulate success directly.

        // Simulate a successful payment immediately for demonstration
        // In a real app, this would be handled by a webhook/callback from the gateway
        processGatewayCallback(payment.getTransactionRef(), PaymentStatus.SUCCESS, "SIMULATED_SUCCESS", orderId, productIdForInventory, amount.intValue()); // Assuming amount.intValue() is the quantity for simplicity
        
        return payment;
    }

    // This method would typically be called by a webhook from the payment gateway
    @Transactional
    public Payment processGatewayCallback(String transactionRef, PaymentStatus newStatus, String gatewayResponse, String orderId, String productIdForInventory, int quantityConfirmed) {
        log.info("Processing payment gateway callback for transactionRef: {} with status: {}", transactionRef, newStatus);
        Payment payment = paymentRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new RuntimeException("Payment record not found for transactionRef: " + transactionRef));

        if (payment.getStatus().equals(PaymentStatus.PENDING)) {
            payment.setStatus(newStatus);
            payment.setGatewayResponse(gatewayResponse);
            payment.setPaymentDate(LocalDateTime.now()); // Update payment date to confirmation time
            payment = paymentRepository.save(payment);

            if (newStatus.equals(PaymentStatus.SUCCESS)) {
                log.info("Payment SUCCESS for orderId: {}. Deducting stock.", payment.getOrderId());
                // Crucial: Deduct reserved stock upon successful payment
                // This assumes quantityConfirmed is the actual quantity of items from the order.
                // In a real scenario, you'd fetch the order details here to get product IDs and quantities.
                inventoryService.confirmReservationAndDeductStock(productIdForInventory, quantityConfirmed);
                // After successful payment, update order status (e.g., to 'PAID' or 'PROCESSING')
                // This would involve calling an OrderService method: orderService.updateOrderStatus(payment.getOrderId(), OrderStatus.PAID);
            } else if (newStatus.equals(PaymentStatus.FAILED) || newStatus.equals(PaymentStatus.REFUNDED)) {
                log.warn("Payment FAILED/REFUNDED for orderId: {}. Releasing reserved stock.", payment.getOrderId());
                // Release reserved stock if payment fails
                // This assumes quantityConfirmed is the actual quantity of items from the order.
                // In a real scenario, you'd fetch the order details here to get product IDs and quantities.
                inventoryService.releaseStock(productIdForInventory, quantityConfirmed);
                // Update order status to 'PAYMENT_FAILED' or 'CANCELLED'
                // orderService.updateOrderStatus(payment.getOrderId(), OrderStatus.PAYMENT_FAILED);
            }
        } else {
            log.warn("Payment for transactionRef: {} already processed with status: {}", transactionRef, payment.getStatus());
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPaymentDetails(String orderId) {
        log.info("Fetching payment details for orderId: {}", orderId);
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));
    }
}