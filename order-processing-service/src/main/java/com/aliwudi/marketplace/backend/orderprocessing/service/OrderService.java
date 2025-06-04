// src/main/java/com/marketplace/emarketplacebackend/service/OrderService.java
package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.model.Product;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.intersevice.ProductIntegrationService;
import com.aliwudi.marketplace.backend.common.intersevice.UserIntegrationService;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.model.Order;
import com.aliwudi.marketplace.backend.common.model.OrderItem;
import com.aliwudi.marketplace.backend.common.status.OrderStatus;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable; // Import for pagination

import java.math.BigDecimal;
import java.time.LocalDateTime; // Import for time range queries
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserIntegrationService userIntegrationService;
    private final ProductIntegrationService productIntegrationService;

    @Autowired
    public OrderService(OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            UserIntegrationService userIntegrationService,
            ProductIntegrationService productIntegrationService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userIntegrationService = userIntegrationService;
        this.productIntegrationService = productIntegrationService;
    }

    /**
     * Creates a new order for a user, processes order items, checks stock,
     * decreases stock, and saves the order.
     *
     * @param userId The ID of the user placing the order.
     * @param itemRequests A list of requested order items (productId,
     * quantity).
     * @param shippingAddress The shipping address for the order.
     * @param paymentMethod The payment method for the order.
     * @return A Mono emitting the created Order.
     */
    public Mono<Order> createOrder(Long userId, List<OrderItemRequest> itemRequests, String shippingAddress, String paymentMethod) {
        Order order = new Order();
        order.setUserId(userId);
        order.setShippingAddress(shippingAddress);
        order.setPaymentMethod(paymentMethod);

        BigDecimal[] totalOrderAmount = {BigDecimal.ZERO}; // Use an array for mutable BigDecimal in lambda

        // Process each item request reactively
        return Flux.fromIterable(itemRequests)
                .flatMap(request -> productIntegrationService.getProductById(request.getProductId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with ID: " + request.getProductId())))
                .flatMap(product -> {
                    if (product.getStockQuantity() == null || product.getStockQuantity() < request.getQuantity()) {
                        return Mono.error(new InsufficientStockException(
                                "Insufficient stock for product: " + product.getName()
                                + ". Available: " + (product.getStockQuantity() != null ? product.getStockQuantity() : 0)
                                + ", Requested: " + request.getQuantity()));
                    }

                    // Decrease stock reactively
                    return productIntegrationService.decreaseAndSaveStock(product.getId(), request.getQuantity())
                            .thenReturn(product); // Pass the product along the chain
                })
                .map(product -> {
                    OrderItem orderItem = OrderItem.builder()
                            .orderId(order.getId())// Link order item to the order
                            .productId(product.getId())
                            .product(product)
                            .quantity(request.getQuantity())
                            .priceAtTimeOfOrder(product.getPrice())                            
                            .build(); 

                    totalOrderAmount[0] = totalOrderAmount[0].add(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
                    return orderItem;
                })
                )
                .collectList() // Collect all processed OrderItems into a List
                .flatMap(orderItems -> {
                    order.setItems(orderItems); // Set the collected items on the order
                    order.setTotalAmount(totalOrderAmount[0]); // Set the calculated total amount

                    // Save the order
                    return orderRepository.save(order)
                            .flatMap(savedOrder
                                    -> // Save each order item linked to the saved order
                                    Flux.fromIterable(orderItems)
                                    .doOnNext(item -> item.setOrderId(savedOrder.getId())) // Ensure item has the saved order's ID
                                    .flatMap(orderItemRepository::save)
                                    .then(Mono.just(savedOrder)) // Return the saved order after all items are saved
                            );
                });
    }

    /**
     * Retrieves all orders.
     *
     * @return A Flux emitting all orders.
     */
    public Flux<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    /**
     * Retrieves an order by its ID.
     *
     * @param id The ID of the order.
     * @return A Mono emitting the order if found, or Mono.empty() if not.
     */
    public Mono<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    /**
     * Retrieves orders by a specific user ID.
     *
     * @param userId The ID of the user.
     * @return A Flux emitting orders for the given user.
     */
    public Flux<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId, Pageable.unpaged()); // Using unpaged to get all
    }

    /**
     * Updates the status of an existing order.
     *
     * @param orderId The ID of the order to update.
     * @param newStatus The new status for the order.
     * @return A Mono emitting the updated Order.
     */
    public Mono<Order> updateOrderStatus(Long orderId, OrderStatus newStatus) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + orderId)))
                .flatMap(order -> {
                    order.setOrderStatus(newStatus);
                    return orderRepository.save(order);
                });
    }

    /**
     * Deletes an order by its ID. This will also cascade delete associated
     * OrderItems if configured in your entity mapping. If not, you might need
     * to manually delete OrderItems first.
     *
     * @param id The ID of the order to delete.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteOrderById(Long id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + id)))
                .flatMap(order
                        -> // First delete associated order items to avoid foreign key constraints
                        orderItemRepository.deleteByOrderId(order.getId()) // Assuming deleteByOrderId exists
                        .then(orderRepository.delete(order)) // Then delete the order itself
                )
                .then(); // Ensure it returns Mono<Void>
    }

    /**
     * Deletes all orders associated with a specific user ID. This will first
     * delete all order items belonging to those orders, then delete the orders
     * themselves.
     *
     * @param userId The ID of the user whose orders are to be deleted.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId, Pageable.unpaged()) // Find all orders for the user
                .flatMap(order
                        -> // For each order, first delete its items
                        orderItemRepository.deleteByOrderId(order.getId()) // Assuming deleteByOrderId exists
                        .then(Mono.just(order)) // Pass the order along
                )
                .collectList() // Collect all orders into a list
                .flatMap(orders
                        -> // After all items are deleted, delete the orders themselves
                        orderRepository.deleteAll(orders) // Use deleteAll with a collection
                )
                .then(); // Ensure it returns Mono<Void>
    }

    /**
     * Deletes a specific order item from an order.
     *
     * @param orderId The ID of the order the item belongs to.
     * @param orderItemId The ID of the order item to delete.
     * @return A Mono<Void> indicating completion, or
     * Mono.error(ResourceNotFoundException) if not found.
     */
    public Mono<Void> deleteOrderItem(Long orderId, Long orderItemId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + orderId)))
                .flatMap(order
                        -> orderItemRepository.findById(orderItemId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order Item not found with ID: " + orderItemId + " in order " + orderId)))
                        .flatMap(orderItem -> {
                            if (!orderItem.getOrderId().equals(order.getId())) { // Use getOrderId() instead of getOrder().getId()
                                return Mono.error(new IllegalArgumentException("Order item " + orderItemId + " does not belong to order " + orderId));
                            }
                            // Before deleting, you might want to return stock (if needed)
                            return orderItemRepository.delete(orderItem)
                                    .doOnSuccess(v -> {
                                        // Optional: Update the order's total amount and save it if you want to reflect changes immediately
                                        // This requires re-fetching items or maintaining a collection
                                        // For simplicity, we're just deleting the item here.
                                        // If Order has a collection of OrderItems and is eagerly loaded, update it
                                        // order.getOrderItems().remove(orderItem); // This line might cause issues if OrderItems is not eagerly loaded or managed
                                        // Recalculate total amount or adjust it
                                        order.setTotalAmount(order.getTotalAmount().subtract(orderItem.getPriceAtTimeOfOrder().multiply(BigDecimal.valueOf(orderItem.getQuantity()))));
                                        orderRepository.save(order).subscribe(); // Non-blocking fire-and-forget for order update
                                    })
                                    .then(); // Return Mono<Void> after deletion
                        })
                );
    }

    /**
     * Clears all orders from the system. Use with extreme caution, typically
     * only for testing or specific reset scenarios.
     *
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> clearAllOrders() {
        // First delete all order items to avoid foreign key constraints
        // then delete all orders.
        return orderItemRepository.deleteAll()
                .then(orderRepository.deleteAll())
                .then();
    }

    @Data // Generates getters and setters for this DTO
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        private Long productId;
        private Integer quantity;
    }

    // --- OrderItemRepository Implementations ---
    /**
     * Retrieves all order items with pagination.
     *
     * @param pageable Pagination information.
     * @return A Flux of OrderItem.
     */
    public Flux<OrderItem> findAllOrderItems(Pageable pageable) {
        return orderItemRepository.findAllBy(pageable);
    }

    /**
     * Retrieves all order items belonging to a specific order with pagination.
     *
     * @param orderId The ID of the order.
     * @param pageable Pagination information.
     * @return A Flux of OrderItem.
     */
    public Flux<OrderItem> findOrderItemsByOrderId(Long orderId, Pageable pageable) {
        return orderItemRepository.findByOrderId(orderId, pageable);
    }

    /**
     * Retrieves all order items containing a specific product with pagination.
     *
     * @param productId The ID of the product.
     * @param pageable Pagination information.
     * @return A Flux of OrderItem.
     */
    public Flux<OrderItem> findOrderItemsByProductId(Long productId, Pageable pageable) {
        return orderItemRepository.findByProductId(productId, pageable);
    }

    /**
     * Finds a specific order item by order ID and product ID.
     *
     * @param orderId The ID of the order.
     * @param productId The ID of the product.
     * @return A Mono emitting the OrderItem.
     */
    public Mono<OrderItem> findSpecificOrderItem(Long orderId, Long productId) {
        return orderItemRepository.findByOrderIdAndProductId(orderId, productId);
    }

    /**
     * Counts all order items.
     *
     * @return A Mono emitting the count.
     */
    public Mono<Long> countAllOrderItems() {
        return orderItemRepository.count();
    }

    /**
     * Counts all order items for a specific order.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countOrderItemsByOrderId(Long orderId) {
        return orderItemRepository.countByOrderId(orderId);
    }

    /**
     * Counts all order items for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countOrderItemsByProductId(Long productId) {
        return orderItemRepository.countByProductId(productId);
    }

    /**
     * Check if a specific product exists within a specific order.
     *
     * @param orderId The ID of the order.
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> checkOrderItemExists(Long orderId, Long productId) {
        return orderItemRepository.existsByOrderIdAndProductId(orderId, productId);
    }

    // --- OrderRepository Implementations ---
    /**
     * Retrieves all orders with pagination.
     *
     * @param pageable Pagination information.
     * @return A Flux of Order.
     */
    public Flux<Order> findAllOrders(Pageable pageable) {
        return orderRepository.findAllBy(pageable);
    }

    /**
     * Finds orders placed by a specific user with pagination.
     *
     * @param userId The ID of the user.
     * @param pageable Pagination information.
     * @return A Flux of Order.
     */
    public Flux<Order> findOrdersByUserId(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    /**
     * Finds orders by their current status with pagination.
     *
     * @param orderStatus The status of the order.
     * @param pageable Pagination information.
     * @return A Flux of Order.
     */
    public Flux<Order> findOrdersByStatus(OrderStatus orderStatus, Pageable pageable) {
        return orderRepository.findByOrderStatus(orderStatus, pageable);
    }

    /**
     * Finds orders placed within a specific time range with pagination.
     *
     * @param startTime The start time of the range.
     * @param endTime The end time of the range.
     * @param pageable Pagination information.
     * @return A Flux of Order.
     */
    public Flux<Order> findOrdersByTimeRange(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return orderRepository.findByOrderTimeBetween(startTime, endTime, pageable);
    }

    /**
     * Finds orders by a specific user and status with pagination.
     *
     * @param userId The ID of the user.
     * @param orderStatus The status of the order.
     * @param pageable Pagination information.
     * @return A Flux of Order.
     */
    public Flux<Order> findOrdersByUserIdAndStatus(Long userId, OrderStatus orderStatus, Pageable pageable) {
        return orderRepository.findByUserIdAndOrderStatus(userId, orderStatus, pageable);
    }

    /**
     * Counts all orders.
     *
     * @return A Mono emitting the count.
     */
    public Mono<Long> countAllOrders() {
        return orderRepository.count();
    }

    /**
     * Counts orders placed by a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countOrdersByUserId(Long userId) {
        return orderRepository.countByUserId(userId);
    }

    /**
     * Counts orders by their current status.
     *
     * @param orderStatus The status of the order.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countOrdersByStatus(OrderStatus orderStatus) {
        return orderRepository.countByOrderStatus(orderStatus);
    }

    /**
     * Counts orders placed within a specific time range.
     *
     * @param startTime The start time of the range.
     * @param endTime The end time of the range.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countOrdersByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return orderRepository.countByOrderTimeBetween(startTime, endTime);
    }

    /**
     * Counts orders by a specific user and status.
     *
     * @param userId The ID of the user.
     * @param orderStatus The status of the order.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countOrdersByUserIdAndStatus(Long userId, OrderStatus orderStatus) {
        return orderRepository.countByUserIdAndOrderStatus(userId, orderStatus);
    }
}
