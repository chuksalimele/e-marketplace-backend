package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.status.PaymentStatus;
import com.aliwudi.marketplace.backend.common.model.Payment;
import com.aliwudi.marketplace.backend.common.model.Order; // Import Order model for prepareDto
import com.aliwudi.marketplace.backend.orderprocessing.repository.PaymentRepository;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.InsufficientStockException; // Keep if stock logic affects payment
import com.aliwudi.marketplace.backend.common.status.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List; // For prepareDto
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService; // To interact with inventory after payment
    private final OrderService orderService; // To fetch order details for prepareDto and update order status

    // IMPORTANT: This prepareDto method is moved from the controller
    // and kept *exactly* as provided by you. It is now a private helper method
    // within the service to enrich the entities before they are returned.
    /**
     * Helper method to map Payment entity to Payment DTO for public exposure.
     * This method enriches the Payment object with Order details
     * by making integration calls.
     */
    private Mono<Payment> prepareDto(Payment payment) {
        if (payment == null) {
            return Mono.empty();
        }
        Mono<Order> orderMono; // Renamed to avoid conflict with 'Order' class
        List<Mono<?>> listMonos = new java.util.ArrayList<>(); // Use ArrayList for mutable list

        if (payment.getOrder() == null && payment.getOrderId() != null) {
            orderMono = orderService.getOrderById(payment.getOrderId());
            listMonos.add(orderMono);
        }

        if (listMonos.isEmpty()) {
            return Mono.just(payment);
        }

        return Mono.zip(listMonos, (Object[] array) -> {
            for (Object obj : array) {
                if (obj instanceof Order order) {
                    payment.setOrder(order);
                }
            }
            return payment;
        });
    }

    /**
     * Initiates a payment for a given order. Creates a pending payment record.
     *
     * @param orderId The ID of the order.
     * @param amount The amount of the payment.
     * @return A Mono emitting the created Payment (enriched).
     * @throws ResourceNotFoundException if the order for which payment is initiated is not found.
     */
    @Transactional
    public Mono<Payment> initiatePayment(Long orderId, BigDecimal amount) {
        log.info("Initiating payment for orderId: {}, amount: {}", orderId, amount);

        // Ensure the order exists before initiating payment
        return orderService.getOrderById(orderId) // getOrderById already throws ResourceNotFoundException
                .flatMap(order -> {
                    // 1. Create a pending payment record
                    Payment payment = Payment.builder()
                            .orderId(orderId)
                            .userId(order.getUserId()) // Set userId from order
                            .amount(amount)
                            .transactionRef(UUID.randomUUID().toString()) // Generate a unique transaction reference
                            .status(PaymentStatus.PENDING)
                            .createdAt(LocalDateTime.now())
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
                                // Need to fetch actual quantity from order items for correct stock deduction
                                orderService.findOrderItemsByOrderId(savedPayment.getOrderId())
                                    .flatMap(orderItem -> {
                                        // Assume for simplicity that payment success means all items are confirmed.
                                        // In a real scenario, you'd confirm per item or based on the payment success logic.
                                        // For now, let's just use the quantity from the first item as a placeholder
                                        // or aggregate if multiple items, but the prompt implies simple deduction.
                                        // The previous `processGatewayCallback` just passed `1` as quantity, which is not ideal.
                                        // Let's pass the actual quantity from the order item for correct stock confirmation.
                                        return processGatewayCallback(savedPayment.getTransactionRef(), PaymentStatus.SUCCESS, "SIMULATED_SUCCESS", savedPayment.getOrderId(), orderItem.getQuantity());
                                    })
                                    .collectList() // Collect results if multiple order items
                                    .thenReturn(savedPayment) // Return the original savedPayment after callback processing
                            )
                            .flatMap(this::prepareDto); // Enrich the payment before returning
                });
    }

    /**
     * Processes payment gateway callbacks, updating payment status and managing inventory.
     * This method is transactional.
     *
     * @param transactionRef The unique transaction reference from the gateway.
     * @param newStatus The new payment status (e.g., SUCCESS, FAILED).
     * @param gatewayResponse The raw response from the payment gateway.
     * @param orderId The ID of the order associated with the payment.
     * @param quantityConfirmed The quantity to confirm/release from inventory (should come from order items).
     * @return A Mono emitting the updated Payment (enriched).
     * @throws ResourceNotFoundException if the payment record is not found.
     * @throws IllegalArgumentException if the newStatus is invalid or if the payment was already processed.
     * @throws InsufficientStockException if there's an issue with inventory operations.
     */
    @Transactional
    public Mono<Payment> processGatewayCallback(String transactionRef, PaymentStatus newStatus, String gatewayResponse, Long orderId, int quantityConfirmed) {
        log.info("Processing payment gateway callback for transactionRef: {} with status: {}", transactionRef, newStatus);

        return paymentRepository.findByTransactionRef(transactionRef)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Payment record not found for transactionRef: " + transactionRef)))
                .flatMap(payment -> {
                    if (!payment.getStatus().equals(PaymentStatus.PENDING)) {
                        log.warn("Payment for transactionRef: {} already processed with status: {}", transactionRef, payment.getStatus());
                        // If payment is already processed, just return it.
                        return Mono.just(payment);
                    }

                    payment.setStatus(newStatus);
                    payment.setGatewayResponse(gatewayResponse);
                    payment.setCreatedAt(LocalDateTime.now()); 
                    payment.setUpdatedAt(LocalDateTime.now()); // Update last modified timestamp

                    return paymentRepository.save(payment)
                            .flatMap(savedPayment -> {
                                Mono<Void> inventoryOperation = Mono.empty();
                                Mono<Void> orderStatusUpdate = Mono.empty();

                                if (newStatus.equals(PaymentStatus.SUCCESS)) {
                                    log.info("Payment SUCCESS for orderId: {}. Deducting stock and updating order status.", savedPayment.getOrderId());
                                    // Fetch order items to get actual quantities for stock deduction
                                    inventoryOperation = orderService.findOrderItemsByOrderId(savedPayment.getOrderId())
                                        .flatMap(orderItem ->
                                            inventoryService.confirmReservationAndDeductStock(orderItem.getProductId(), orderItem.getQuantity())
                                        ).then(); // Combine all stock deduction operations
                                    orderStatusUpdate = orderService.updateOrderStatus(savedPayment.getOrderId(), OrderStatus.PAID).then();
                                } else if (newStatus.equals(PaymentStatus.FAILED) || newStatus.equals(PaymentStatus.REFUNDED)) {
                                    log.warn("Payment FAILED/REFUNDED for orderId: {}. Releasing reserved stock and updating order status.", savedPayment.getOrderId());
                                    // Fetch order items to get actual quantities for stock release
                                    inventoryOperation = orderService.findOrderItemsByOrderId(savedPayment.getOrderId())
                                        .flatMap(orderItem ->
                                            inventoryService.releaseStock(orderItem.getProductId(), orderItem.getQuantity())
                                        ).then(); // Combine all stock release operations
                                    orderStatusUpdate = orderService.updateOrderStatus(savedPayment.getOrderId(), OrderStatus.PAYMENT_FAILED).then(); // Or CANCELLED
                                }

                                // Execute inventory and order status operations concurrently, then return the saved payment
                                return Mono.when(inventoryOperation, orderStatusUpdate).thenReturn(savedPayment);
                            })
                            .flatMap(this::prepareDto) // Enrich the updated payment before returning
                            .doOnSuccess(p -> log.info("Payment callback processed for transactionRef: {}. New status: {}", transactionRef, newStatus))
                            .doOnError(e -> log.error("Error during payment callback processing for transactionRef {}: {}", transactionRef, e.getMessage(), e));
                });
    }

    /**
     * Fetches payment details for a given order ID, enriching the payment.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the Payment details (enriched).
     * @throws ResourceNotFoundException if payment for the order is not found.
     */
    @Transactional(readOnly = true)
    public Mono<Payment> getPaymentDetails(Long orderId) {
        log.info("Fetching payment details for orderId: {}", orderId);
        return paymentRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Payment not found for orderId: " + orderId)))
                .flatMap(this::prepareDto); // Enrich the payment
    }

    // --- PaymentRepository Implementations (Public Service Methods) ---

    /**
     * Retrieves all payments with pagination, enriching each payment.
     *
     * @param pageable Pagination information.
     * @return A Flux of Payment records (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Payment> findAllPayments(Pageable pageable) {
        log.info("Finding all payments with pagination: {}", pageable);
        return paymentRepository.findAllBy(pageable)
                .flatMap(this::prepareDto); // Enrich each payment
    }

    /**
     * Finds payments made by a specific user with pagination, enriching each payment.
     *
     * @param userId The ID of the user.
     * @param pageable Pagination information.
     * @return A Flux of Payment records for the specified user (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Payment> findPaymentsByUserId(Long userId, Pageable pageable) {
        log.info("Finding payments for user: {} with pagination: {}", userId, pageable);
        return paymentRepository.findByUserId(userId, pageable)
                .flatMap(this::prepareDto); // Enrich each payment
    }

    /**
     * Finds payments by their status with pagination, enriching each payment.
     *
     * @param status The payment status.
     * @param pageable Pagination information.
     * @return A Flux of Payment records with the specified status (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Payment> findPaymentsByStatus(PaymentStatus status, Pageable pageable) {
        log.info("Finding payments with status: {} with pagination: {}", status, pageable);
        return paymentRepository.findByStatus(status, pageable)
                .flatMap(this::prepareDto); // Enrich each payment
    }

    /**
     * Finds payments made within a specific time range with pagination, enriching each payment.
     *
     * @param startTime The start of the time range.
     * @param endTime The end of the time range.
     * @param pageable Pagination information.
     * @return A Flux of Payment records within the specified time range (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Payment> findPaymentsByPaymentTimeBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        log.info("Finding payments between {} and {} with pagination: {}", startTime, endTime, pageable);
        return paymentRepository.findByPaymentTimeBetween(startTime, endTime, pageable)
                .flatMap(this::prepareDto); // Enrich each payment
    }

    /**
     * Finds a payment by its unique transaction reference, enriching it.
     *
     * @param transactionRef The unique transaction reference.
     * @return A Mono emitting the Payment record if found (enriched).
     * @throws ResourceNotFoundException if the payment is not found.
     */
    @Transactional(readOnly = true)
    public Mono<Payment> findPaymentByTransactionRef(String transactionRef) {
        log.info("Finding payment by transaction reference: {}", transactionRef);
        return paymentRepository.findByTransactionRef(transactionRef)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Payment not found for transaction reference: " + transactionRef)))
                .flatMap(this::prepareDto); // Enrich the payment
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
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the count of payments for the specified user.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countPaymentsByUserId(Long userId) {
        log.info("Counting payments for user: {}", userId);
        return paymentRepository.countByUserId(userId);
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
