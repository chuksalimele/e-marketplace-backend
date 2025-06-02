package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.status.PaymentStatus;
import com.aliwudi.marketplace.backend.orderprocessing.model.Payment;
import com.aliwudi.marketplace.backend.orderprocessing.repository.PaymentRepository;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException; // Import ResourceNotFoundException
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux for methods returning multiple items
import org.springframework.data.domain.Pageable; // Import for pagination

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
                    // Note: The quantityConfirmed here is a placeholder, in a real scenario, you'd fetch it from the order.
                    processGatewayCallback(savedPayment.getTransactionRef(), PaymentStatus.SUCCESS, "SIMULATED_SUCCESS", savedPayment.getOrderId(), 1) // Assuming quantity 1 for simplicity
                        .thenReturn(savedPayment) // Return the original savedPayment after callback processing
                );
    }

    // This method would typically be called by a webhook from the payment gateway
    @Transactional
    public Mono<Payment> processGatewayCallback(String transactionRef, PaymentStatus newStatus, String gatewayResponse, String orderId, int quantityConfirmed) {
        log.info("Processing payment gateway callback for transactionRef: {} with status: {}", transactionRef, newStatus);

        return paymentRepository.findByTransactionRef(transactionRef) // Returns Mono<Payment>
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Payment record not found for transactionRef: " + transactionRef)))
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
                                        // Ensure productId is correctly passed from orderId (assuming orderId is also productId for simplicity here)
                                        inventoryOperation = inventoryService.confirmReservationAndDeductStock(savedPayment.getOrderId(), quantityConfirmed);
                                        // After successful payment, update order status (e.g., to 'PAID' or 'PROCESSING')
                                        // This would involve calling an OrderService method: orderService.updateOrderStatus(payment.getOrderId(), OrderStatus.PAID);
                                    } else if (newStatus.equals(PaymentStatus.FAILED) || newStatus.equals(PaymentStatus.REFUNDED)) {
                                        log.warn("Payment FAILED/REFUNDED for orderId: {}. Releasing reserved stock.", savedPayment.getOrderId());
                                        // Release reserved stock if payment fails
                                        // Ensure productId is correctly passed from orderId (assuming orderId is also productId for simplicity here)
                                        inventoryOperation = inventoryService.releaseStock(savedPayment.getOrderId(), quantityConfirmed);
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

    /**
     * Fetches payment details for a given order ID.
     * Converts String orderId to Long for repository interaction.
     *
     * @param orderId The ID of the order (as String).
     * @return A Mono emitting the Payment details.
     * @throws ResourceNotFoundException if payment for the order is not found.
     */
    @Transactional(readOnly = true)
    public Mono<Payment> getPaymentDetails(String orderId) {
        log.info("Fetching payment details for orderId: {}", orderId);
        return Mono.just(Long.parseLong(orderId)) // Convert String to Long
                .flatMap(longOrderId -> paymentRepository.findByOrderId(longOrderId))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Payment not found for orderId: " + orderId)));
    }

    // --- NEW: PaymentRepository Implementations ---

    /**
     * Retrieves all payments with pagination.
     *
     * @param pageable Pagination information.
     * @return A Flux of Payment records.
     */
    @Transactional(readOnly = true)
    public Flux<Payment> findAllPayments(Pageable pageable) {
        log.info("Finding all payments with pagination: {}", pageable);
        return paymentRepository.findAllBy(pageable);
    }

    /**
     * Finds payments made by a specific user with pagination.
     * Converts String userId to Long for repository interaction.
     *
     * @param userId The ID of the user (as String).
     * @param pageable Pagination information.
     * @return A Flux of Payment records for the specified user.
     */
    @Transactional(readOnly = true)
    public Flux<Payment> findPaymentsByUserId(String userId, Pageable pageable) {
        log.info("Finding payments for user: {} with pagination: {}", userId, pageable);
        return Mono.just(Long.parseLong(userId)) // Convert String to Long
                .flatMapMany(longUserId -> paymentRepository.findByUserId(longUserId, pageable));
    }

    /**
     * Finds payments by their status with pagination.
     *
     * @param status The payment status.
     * @param pageable Pagination information.
     * @return A Flux of Payment records with the specified status.
     */
    @Transactional(readOnly = true)
    public Flux<Payment> findPaymentsByStatus(PaymentStatus status, Pageable pageable) {
        log.info("Finding payments with status: {} with pagination: {}", status, pageable);
        return paymentRepository.findByStatus(status, pageable);
    }

    /**
     * Finds payments made within a specific time range with pagination.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @param pageable Pagination information.
     * @return A Flux of Payment records within the specified time range.
     */
    @Transactional(readOnly = true)
    public Flux<Payment> findPaymentsByPaymentTimeBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        log.info("Finding payments between {} and {} with pagination: {}", startTime, endTime, pageable);
        return paymentRepository.findByPaymentTimeBetween(startTime, endTime, pageable);
    }

    /**
     * Finds a payment by its unique transaction reference.
     *
     * @param transactionRef The unique transaction reference.
     * @return A Mono emitting the Payment record if found.
     */
    @Transactional(readOnly = true)
    public Mono<Payment> findPaymentByTransactionRef(String transactionRef) {
        log.info("Finding payment by transaction reference: {}", transactionRef);
        return paymentRepository.findByTransactionRef(transactionRef);
    }

    /**
     * Counts all payments.
     *
     * @return A Mono emitting the total count of payments.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countAllPayments() {
        log.info("Counting all payments.");
        return paymentRepository.count();
    }

    /**
     * Counts payments made by a specific user.
     * Converts String userId to Long for repository interaction.
     *
     * @param userId The ID of the user (as String).
     * @return A Mono emitting the count of payments for the specified user.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countPaymentsByUserId(String userId) {
        log.info("Counting payments for user: {}", userId);
        return Mono.just(Long.parseLong(userId)) // Convert String to Long
                .flatMap(longUserId -> paymentRepository.countByUserId(longUserId));
    }

    /**
     * Counts payments by their status.
     *
     * @param status The payment status.
     * @return A Mono emitting the count of payments with the specified status.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countPaymentsByStatus(PaymentStatus status) {
        log.info("Counting payments with status: {}", status);
        return paymentRepository.countByStatus(status);
    }

    /**
     * Counts payments made within a specific time range.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @return A Mono emitting the count of payments within the specified time range.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countPaymentsByPaymentTimeBetween(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Counting payments between {} and {}", startTime, endTime);
        return paymentRepository.countByPaymentTimeBetween(startTime, endTime);
    }

    /**
     * Checks if a payment with a given transaction reference exists.
     *
     * @param transactionRef The unique transaction reference.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    @Transactional(readOnly = true)
    public Mono<Boolean> existsPaymentByTransactionRef(String transactionRef) {
        log.info("Checking if payment exists for transaction reference: {}", transactionRef);
        return paymentRepository.existsByTransactionRef(transactionRef);
    }
}