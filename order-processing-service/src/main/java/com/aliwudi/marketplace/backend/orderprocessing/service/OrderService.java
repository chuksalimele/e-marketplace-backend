package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.interservice.ProductIntegrationService;
import com.aliwudi.marketplace.backend.common.interservice.UserIntegrationService;
import com.aliwudi.marketplace.backend.common.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.model.Order;
import com.aliwudi.marketplace.backend.common.model.OrderItem;
import com.aliwudi.marketplace.backend.common.model.Product; // Import Product model
import com.aliwudi.marketplace.backend.common.model.User;     // Import User model
import com.aliwudi.marketplace.backend.common.status.OrderStatus;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderRepository;
import lombok.RequiredArgsConstructor; // Use Lombok's RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j;       // For logging
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional; // For transaction management

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors; // Added for prepareDto list processing
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Service
@RequiredArgsConstructor // Generates constructor for final fields, replacing @Autowired
@Slf4j // Enables Lombok's logging
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserIntegrationService userIntegrationService;
    private final ProductIntegrationService productIntegrationService;

    // IMPORTANT: These prepareDto methods are moved from the controller
    // and kept *exactly* as provided by you. They are now private helper methods
    // within the service to enrich the entities before they are returned.

    /**
     * Helper method to map Order entity to Order DTO for public exposure.
     * This method enriches the Order object with User and OrderItem details
     * by making integration calls.
     */
    private Mono<Order> prepareDto(Order order) {
        if (order == null) {
            return Mono.empty();
        }

        // List to hold monos for concurrent enrichment
        List<Mono<?>> enrichmentMonos = new java.util.ArrayList<>();

        // If user is not already set, fetch it
        if (order.getUser() == null && order.getUserId() != null) {
            enrichmentMonos.add(userIntegrationService.getUserById(order.getUserId())
                    .doOnNext(order::setUser) // Set user on the order if found
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch user {} for order {}: {}",
                                order.getUserId(), order.getId(), e.getMessage());
                        // If user fetching fails, provide a placeholder or keep null
                        order.setUser(null);
                        return Mono.empty(); // Continue with the enrichment flow
                    })
            );
        }

        // If items are not already set, fetch them
        if (order.getItems() == null) {
            enrichmentMonos.add(orderItemRepository.findByOrderId(order.getId())
                    .flatMap(this::prepareDto) // Recursively enrich each order item
                    .collectList()
                    .doOnNext(order::setItems) // Set items on the order
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch order items for order {}: {}", order.getId(), e.getMessage());
                        return Mono.empty(); // Continue with the enrichment flow
                    })
            );
        }

        // If no enrichment calls are needed, return the order directly
        if (enrichmentMonos.isEmpty()) {
            return Mono.just(order);
        }

        // Zip all enrichment monos. The doOnNext calls above will have already populated the order.
        return Mono.zip(enrichmentMonos, (Object[] results) -> order)
                .defaultIfEmpty(order); // Ensure order is returned even if zip is empty or throws error in one path
    }

    /**
     * Helper method to map OrderItem entity to OrderItem DTO for public exposure.
     * This method enriches the OrderItem object with Product details
     * by making integration calls.
     */
    private Mono<OrderItem> prepareDto(OrderItem orderItem) {
        if (orderItem == null) {
            return Mono.empty();
        }

        // If product is not already set, fetch it
        if (orderItem.getProduct() == null && orderItem.getProductId() != null) {
            return productIntegrationService.getProductById(orderItem.getProductId())
                    .doOnNext(orderItem::setProduct) // Set product on the order item if found
                    .map(product -> orderItem) // Return the modified orderItem
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch product {} for order item {}: {}",
                                orderItem.getProductId(), orderItem.getId(), e.getMessage());
                        orderItem.setProduct(null); // Set product to null if fetching fails
                        return Mono.just(orderItem); // Continue with the orderItem even if product fetch fails
                    });
        }
        return Mono.just(orderItem); // Return the original orderItem if no product fetching needed
    }

    /**
     * Creates a new order for a user, processes order items, checks stock,
     * decreases stock, and saves the order.
     * This method is transactional to ensure atomicity.
     *
     * @param userId The ID of the user placing the order.
     * @param itemRequests A list of requested order items (productId, quantity).
     * @param shippingAddress The shipping address for the order.
     * @param paymentMethod The payment method for the order.
     * @return A Mono emitting the created Order (enriched with details).
     * @throws ResourceNotFoundException if a product or user is not found.
     * @throws InsufficientStockException if there's insufficient stock for any product.
     */
    @Transactional
    public Mono<Order> createOrder(Long userId, List<OrderItemRequest> itemRequests, String shippingAddress, String paymentMethod) {
        Order newOrder = new Order();
        newOrder.setUserId(userId);
        newOrder.setShippingAddress(shippingAddress);
        newOrder.setPaymentMethod(paymentMethod);
        newOrder.setOrderStatus(OrderStatus.PENDING); // Initial status
        newOrder.setOrderTime(LocalDateTime.now()); // Set order creation time

        log.info("Attempting to create order for user {}. Items: {}", userId, itemRequests.size());

        // Ensure user exists and get user details (if needed for enrichment in prepareDto)
        Mono<User> userExistenceCheck = userIntegrationService.getUserById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found with ID: " + userId)));

        // Process each item request: fetch product, check stock, decrease stock
        Flux<OrderItem> processedItemsFlux = Flux.fromIterable(itemRequests)
                .flatMap(request -> productIntegrationService.getProductById(request.getProductId())
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with ID: " + request.getProductId())))
                        .flatMap(product -> {
                            if (product.getStockQuantity() == null || product.getStockQuantity() < request.getQuantity()) {
                                log.warn("Insufficient stock for product {}. Available: {}, Requested: {}",
                                        product.getId(), product.getStockQuantity(), request.getQuantity());
                                return Mono.error(new InsufficientStockException(
                                        "Insufficient stock for product: " + product.getName()
                                        + ". Available: " + (product.getStockQuantity() != null ? product.getStockQuantity() : 0)
                                        + ", Requested: " + request.getQuantity()));
                            }

                            // Decrease stock reactively in the product/inventory service
                            return productIntegrationService.decreaseAndSaveStock(product.getId(), request.getQuantity())
                                    .thenReturn(product); // Pass the product along the chain after stock is decreased
                        })
                        .map(product -> {
                            // Create OrderItem
                            return OrderItem.builder()
                                    .productId(product.getId())
                                    .product(product) // Store product details in OrderItem for snapshot
                                    .quantity(request.getQuantity())
                                    .priceAtTimeOfOrder(product.getPrice())
                                    .createdAt(LocalDateTime.now()) // Set created timestamp for order item
                                    .build();
                        })
                );

        return userExistenceCheck
                .then(processedItemsFlux.collectList() // Collect all processed OrderItems into a List
                        .flatMap(orderItems -> {
                            newOrder.setItems(orderItems); // Set the collected items on the order

                            // Calculate total amount from processed order items
                            BigDecimal totalAmount = orderItems.stream()
                                    .filter(item -> item.getPriceAtTimeOfOrder() != null && item.getQuantity() != null)
                                    .map(item -> item.getPriceAtTimeOfOrder().multiply(BigDecimal.valueOf(item.getQuantity())))
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            newOrder.setTotalAmount(totalAmount);

                            // Save the order
                            return orderRepository.save(newOrder)
                                    .flatMap(savedOrder ->
                                        // Save each order item linked to the saved order
                                        Flux.fromIterable(orderItems)
                                            .doOnNext(item -> item.setOrderId(savedOrder.getId())) // Ensure item has the saved order's ID
                                            .flatMap(orderItemRepository::save) // Save each order item
                                            .then(Mono.just(savedOrder)) // Return the saved order after all items are saved
                                    )
                                    .flatMap(this::prepareDto) // Enrich the saved order before returning
                                    .doOnSuccess(order -> log.info("Order created successfully with ID: {}", order.getId()))
                                    .doOnError(e -> log.error("Failed to save order or order items: {}", e.getMessage(), e));
                        }));
    }

    /**
     * Retrieves all orders with pagination, enriching each order with details.
     *
     * @param pageable Pagination information.
     * @return A Flux emitting all orders (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Order> findAllOrders(Pageable pageable) {
        log.info("Finding all orders with pagination: {}", pageable);
        return orderRepository.findAllBy(pageable)
                .flatMap(this::prepareDto); // Enrich each order
    }

    /**
     * Retrieves an order by its ID, enriching it with details.
     *
     * @param id The ID of the order.
     * @return A Mono emitting the order if found (enriched), or Mono.error(ResourceNotFoundException) if not.
     */
    @Transactional(readOnly = true)
    public Mono<Order> getOrderById(Long id) {
        log.info("Retrieving order by ID: {}", id);
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + id)))
                .flatMap(this::prepareDto); // Enrich the order
    }

    /**
     * Retrieves orders by a specific user ID with pagination, enriching each order.
     *
     * @param userId The ID of the user.
     * @param pageable Pagination information.
     * @return A Flux emitting orders for the given user (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Order> findOrdersByUserId(Long userId, Pageable pageable) {
        log.info("Finding orders by user ID: {} with pagination: {}", userId, pageable);
        return orderRepository.findByUserId(userId, pageable)
                .switchIfEmpty(Flux.error(new ResourceNotFoundException("No orders found for user ID: " + userId)))
                .flatMap(this::prepareDto); // Enrich each order
    }

    /**
     * Updates the status of an existing order.
     *
     * @param orderId The ID of the order to update.
     * @param newStatus The new status for the order.
     * @return A Mono emitting the updated Order (enriched).
     * @throws ResourceNotFoundException if the order is not found.
     */
    @Transactional
    public Mono<Order> updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.info("Updating status for order {} to {}", orderId, newStatus);
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + orderId)))
                .flatMap(order -> {
                    order.setOrderStatus(newStatus);
                    order.setUpdatedAt(LocalDateTime.now()); // Update timestamp
                    return orderRepository.save(order);
                })
                .flatMap(this::prepareDto) // Enrich the updated order
                .doOnSuccess(order -> log.info("Order {} status updated to {}", order.getId(), order.getOrderStatus()));
    }

    /**
     * Deletes an order by its ID. This will also cascade delete associated
     * OrderItems by first deleting them explicitly.
     * This method is transactional.
     *
     * @param id The ID of the order to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the order is not found.
     */
    @Transactional
    public Mono<Void> deleteOrderById(Long id) {
        log.info("Attempting to delete order with ID: {}", id);
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + id)))
                .flatMap(order ->
                    orderItemRepository.deleteByOrderId(order.getId()) // First delete associated order items
                        .then(orderRepository.delete(order))           // Then delete the order itself
                        .doOnSuccess(v -> log.info("Successfully deleted order {} and its items.", id))
                )
                .then(); // Ensure it returns Mono<Void>
    }

    /**
     * Deletes all orders associated with a specific user ID. This will first
     * delete all order items belonging to those orders, then delete the orders
     * themselves.
     * This method is transactional.
     *
     * @param userId The ID of the user whose orders are to be deleted.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if no orders are found for the user.
     */
    @Transactional
    public Mono<Void> deleteOrdersByUserId(Long userId) {
        log.info("Attempting to delete all orders for user ID: {}", userId);
        return orderRepository.findByUserId(userId, Pageable.unpaged()) // Find all orders for the user
                .flatMap(order ->
                    orderItemRepository.deleteByOrderId(order.getId()) // For each order, first delete its items
                        .thenReturn(order) // Pass the order along
                )
                .collectList() // Collect all orders into a list
                .flatMap(orders -> {
                    if (orders.isEmpty()) {
                        log.warn("No orders found for user ID {} to delete.", userId);
                        return Mono.error(new ResourceNotFoundException("No orders found for user ID: " + userId));
                    }
                    return orderRepository.deleteAll(orders) // After all items are deleted, delete the orders themselves
                            .doOnSuccess(v -> log.info("Successfully deleted {} orders for user {}.", orders.size(), userId));
                })
                .then(); // Ensure it returns Mono<Void>
    }

    /**
     * Deletes a specific order item from an order. It also updates the order's total amount.
     * This method is transactional.
     *
     * @param orderId The ID of the order the item belongs to.
     * @param orderItemId The ID of the order item to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the order or order item is not found.
     * @throws IllegalArgumentException if the order item does not belong to the specified order.
     */
    @Transactional
    public Mono<Void> deleteOrderItem(Long orderId, Long orderItemId) {
        log.info("Attempting to delete order item {} from order {}", orderItemId, orderId);
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + orderId)))
                .flatMap(order -> orderItemRepository.findById(orderItemId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order Item not found with ID: " + orderItemId + " in order " + orderId)))
                        .flatMap(orderItem -> {
                            if (!orderItem.getOrderId().equals(order.getId())) {
                                log.warn("Order item {} does not belong to order {}", orderItemId, orderId);
                                return Mono.error(new IllegalArgumentException("Order item " + orderItemId + " does not belong to order " + orderId));
                            }
                            // Calculate new total before deleting item
                            BigDecimal itemValue = orderItem.getPriceAtTimeOfOrder().multiply(BigDecimal.valueOf(orderItem.getQuantity()));
                            BigDecimal newTotal = order.getTotalAmount().subtract(itemValue);

                            // Delete the order item
                            return orderItemRepository.delete(orderItem)
                                    .doOnSuccess(v -> log.info("Deleted order item {} from order {}.", orderItemId, orderId))
                                    .then(Mono.defer(() -> {
                                        // Update order's total amount and save it
                                        order.setTotalAmount(newTotal);
                                        order.setUpdatedAt(LocalDateTime.now());
                                        return orderRepository.save(order);
                                    }))
                                    .then(); // Return Mono<Void> after deletion and order update
                        })
                );
    }

    /**
     * Clears all orders from the system. Use with extreme caution, typically
     * only for testing or specific reset scenarios.
     * This method is transactional.
     *
     * @return A Mono<Void> indicating completion.
     */
    @Transactional
    public Mono<Void> clearAllOrders() {
        log.warn("Clearing all orders and order items from the system. This operation is irreversible.");
        return orderItemRepository.deleteAll() // First delete all order items
                .then(orderRepository.deleteAll()) // Then delete all orders.
                .doOnSuccess(v -> log.info("All orders and order items cleared successfully."))
                .then();
    }

    /**
     * DTO for order item requests in the createOrder method.
     */
    @Data // Generates getters and setters for this DTO
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        private Long productId;
        private Integer quantity;
    }

    // --- OrderItemRepository Implementations (Public Service Methods) ---

    /**
     * Retrieves all order items with pagination, enriching each order item.
     *
     * @param pageable Pagination information.
     * @return A Flux of OrderItem (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<OrderItem> findAllOrderItems(Pageable pageable) {
        log.info("Finding all order items with pagination: {}", pageable);
        return orderItemRepository.findAllBy(pageable)
                .flatMap(this::prepareDto); // Enrich each order item
    }

    /**
     * Retrieves all order items belonging to a specific order with pagination, enriching each order item.
     *
     * @param orderId The ID of the order.
     * @param pageable Pagination information.
     * @return A Flux of OrderItem (enriched).
     * @throws ResourceNotFoundException if no order items are found for the given orderId.
     */
    @Transactional(readOnly = true)
    public Flux<OrderItem> findOrderItemsByOrderId(Long orderId, Pageable pageable) {
        log.info("Finding order items for order ID: {} with pagination: {}", orderId, pageable);
        return orderItemRepository.findByOrderId(orderId, pageable)
                .switchIfEmpty(Flux.error(new ResourceNotFoundException("No order items found for order ID: " + orderId)))
                .flatMap(this::prepareDto); // Enrich each order item
    }

    /**
     * Retrieves all order items belonging to a specific order without pagination, enriching each order item.
     * Assumes a maximum allowable number of order items per order.
     *
     * @param orderId The ID of the order.
     * @return A Flux of OrderItem (enriched).
     * @throws ResourceNotFoundException if no order items are found for the given orderId.
     */
    @Transactional(readOnly = true)
    public Flux<OrderItem> findOrderItemsByOrderId(Long orderId) {
        log.info("Finding all order items for order ID: {}", orderId);
        return orderItemRepository.findByOrderId(orderId)
                .switchIfEmpty(Flux.error(new ResourceNotFoundException("No order items found for order ID: " + orderId)))
                .flatMap(this::prepareDto); // Enrich each order item
    }

    /**
     * Retrieves all order items containing a specific product with pagination, enriching each order item.
     *
     * @param productId The ID of the product.
     * @param pageable Pagination information.
     * @return A Flux of OrderItem (enriched).
     * @throws ResourceNotFoundException if no order items are found for the given productId.
     */
    @Transactional(readOnly = true)
    public Flux<OrderItem> findOrderItemsByProductId(Long productId, Pageable pageable) {
        log.info("Finding order items by product ID: {} with pagination: {}", productId, pageable);
        return orderItemRepository.findByProductId(productId, pageable)
                .switchIfEmpty(Flux.error(new ResourceNotFoundException("No order items found for product ID: " + productId)))
                .flatMap(this::prepareDto); // Enrich each order item
    }

    /**
     * Finds a specific order item by order ID and product ID, enriching it.
     *
     * @param orderId The ID of the order.
     * @param productId The ID of the product.
     * @return A Mono emitting the OrderItem (enriched).
     * @throws ResourceNotFoundException if the order item is not found.
     */
    @Transactional(readOnly = true)
    public Mono<OrderItem> findSpecificOrderItem(Long orderId, Long productId) {
        log.info("Finding order item for order {} and product {}", orderId, productId);
        return orderItemRepository.findByOrderIdAndProductId(orderId, productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order item not found for order " + orderId + " and product " + productId)))
                .flatMap(this::prepareDto); // Enrich the order item
    }

    /**
     * Counts all order items.
     *
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countAllOrderItems() {
        log.info("Counting all order items.");
        return orderItemRepository.count();
    }

    /**
     * Counts all order items for a specific order.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countOrderItemsByOrderId(Long orderId) {
        log.info("Counting order items for order ID: {}", orderId);
        return orderItemRepository.countByOrderId(orderId);
    }

    /**
     * Counts all order items for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countOrderItemsByProductId(Long productId) {
        log.info("Counting order items for product ID: {}", productId);
        return orderItemRepository.countByProductId(productId);
    }

    /**
     * Check if an order exist
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    @Transactional(readOnly = true)
    public Mono<Boolean> checkOrderExists(Long orderId) {
        log.info("Checking if order exists with ID {}", orderId);
        return orderRepository.existsById(orderId);
    }

    /**
     * Check if a specific product exists within a specific order.
     *
     * @param orderId The ID of the order.
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    @Transactional(readOnly = true)
    public Mono<Boolean> checkOrderItemExists(Long orderId, Long productId) {
        log.info("Checking if order item exists for order {} and product {}", orderId, productId);
        return orderItemRepository.existsByOrderIdAndProductId(orderId, productId);
    }

    // --- OrderRepository Implementations (Public Service Methods) ---

    /**
     * Finds orders by their current status with pagination, enriching each order.
     *
     * @param orderStatus The status of the order.
     * @param pageable Pagination information.
     * @return A Flux of Order (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Order> findOrdersByStatus(OrderStatus orderStatus, Pageable pageable) {
        log.info("Finding orders by status: {} with pagination: {}", orderStatus, pageable);
        return orderRepository.findByOrderStatus(orderStatus, pageable)
                .flatMap(this::prepareDto);
    }

    /**
     * Finds orders placed within a specific time range with pagination, enriching each order.
     *
     * @param startTime The start time of the range.
     * @param endTime The end time of the range.
     * @param pageable Pagination information.
     * @return A Flux of Order (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Order> findOrdersByTimeRange(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        log.info("Finding orders by time range: {} to {} with pagination: {}", startTime, endTime, pageable);
        return orderRepository.findByOrderTimeBetween(startTime, endTime, pageable)
                .flatMap(this::prepareDto);
    }

    /**
     * Finds orders by a specific user and status with pagination, enriching each order.
     *
     * @param userId The ID of the user.
     * @param orderStatus The status of the order.
     * @param pageable Pagination information.
     * @return A Flux of Order (enriched).
     */
    @Transactional(readOnly = true)
    public Flux<Order> findOrdersByUserIdAndStatus(Long userId, OrderStatus orderStatus, Pageable pageable) {
        log.info("Finding orders by user ID: {} and status: {} with pagination: {}", userId, orderStatus, pageable);
        return orderRepository.findByUserIdAndOrderStatus(userId, orderStatus, pageable)
                .flatMap(this::prepareDto);
    }

    /**
     * Counts all orders.
     *
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countAllOrders() {
        log.info("Counting all orders.");
        return orderRepository.count();
    }

    /**
     * Counts orders placed by a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countOrdersByUserId(Long userId) {
        log.info("Counting orders by user ID: {}", userId);
        return orderRepository.countByUserId(userId);
    }

    /**
     * Counts orders by their current status.
     *
     * @param orderStatus The status of the order.
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countOrdersByStatus(OrderStatus orderStatus) {
        log.info("Counting orders by status: {}", orderStatus);
        return orderRepository.countByOrderStatus(orderStatus);
    }

    /**
     * Counts orders placed within a specific time range.
     *
     * @param startTime The start time of the range.
     * @param endTime The end time of the range.
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countOrdersByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Counting orders by time range: {} to {}", startTime, endTime);
        return orderRepository.countByOrderTimeBetween(startTime, endTime);
    }

    /**
     * Counts orders by a specific user and status.
     *
     * @param userId The ID of the user.
     * @param orderStatus The status of the order.
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countOrdersByUserIdAndStatus(Long userId, OrderStatus orderStatus) {
        log.info("Counting orders by user ID: {} and status: {}", userId, orderStatus);
        return orderRepository.countByUserIdAndOrderStatus(userId, orderStatus);
    }
}
