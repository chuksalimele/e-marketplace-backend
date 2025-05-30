// src/main/java/com/marketplace/emarketplacebackend/service/OrderService.java
package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.dto.ProductDto;
import com.aliwudi.marketplace.backend.common.dto.UserDto;
import com.aliwudi.marketplace.backend.common.intersevice.ProductIntegrationService;
import com.aliwudi.marketplace.backend.common.intersevice.UserIntegrationService;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.model.Order;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderItem;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderStatus;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
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
     * @param itemRequests A list of requested order items (productId, quantity).
     * @param shippingAddress The shipping address for the order.
     * @param paymentMethod The payment method for the order.
     * @return A Mono emitting the created Order.
     */
    public Mono<Order> createOrder(Long userId, List<OrderItemRequest> itemRequests, String shippingAddress, String paymentMethod) {
        // Step 1: Validate User exists (optional, depends on your business logic)
        // If user validation is critical for order creation, uncomment and handle.
        // For simplicity, we'll assume userId is valid and proceed with order creation.
        // If you need to ensure user exists from user service:
        // return userIntegrationService.getUserById(userId)
        //        .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found with ID: " + userId)))
        //        .flatMap(userDto -> { ... rest of the logic ... });


        Order order = new Order();
        order.setUserId(userId);
        order.setShippingAddress(shippingAddress);
        order.setPaymentMethod(paymentMethod);
        // orderDate and orderStatus are set by @PrePersist in Order entity, or can be set here.
        // order.setOrderDate(LocalDateTime.now()); // Assuming you have a default in entity
        // order.setOrderStatus(OrderStatus.PENDING); // Assuming you have a default in entity

        BigDecimal[] totalOrderAmount = {BigDecimal.ZERO}; // Use an array for mutable BigDecimal in lambda

        // Process each item request reactively
        return Flux.fromIterable(itemRequests)
                .flatMap(request -> productIntegrationService.getProductDtoById(request.getProductId())
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with ID: " + request.getProductId())))
                        .flatMap(product -> {
                            if (product.getStock() == null || product.getStock() < request.getQuantity()) {
                                return Mono.error(new InsufficientStockException(
                                        "Insufficient stock for product: " + product.getName() +
                                                ". Available: " + (product.getStock() != null ? product.getStock() : 0) +
                                                ", Requested: " + request.getQuantity()));
                            }

                            // Decrease stock reactively
                            return productIntegrationService.decreaseAndSaveStock(product.getId(), request.getQuantity())
                                    .thenReturn(product); // Pass the product along the chain
                        })
                        .map(product -> {
                            OrderItem orderItem = new OrderItem();
                            orderItem.setProductId(product.getId());
                            orderItem.setQuantity(request.getQuantity());
                            orderItem.setPriceAtTimeOfOrder(product.getPrice());
                            orderItem.setOrder(order); // Link order item to the order (important for persistence)
                            totalOrderAmount[0] = totalOrderAmount[0].add(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
                            return orderItem;
                        })
                )
                .collectList() // Collect all processed OrderItems into a List
                .flatMap(orderItems -> {
                    order.setOrderItems(orderItems); // Set the collected items on the order
                    order.setTotalAmount(totalOrderAmount[0]); // Set the calculated total amount

                    // Save the order
                    return orderRepository.save(order)
                            .flatMap(savedOrder ->
                                // Save each order item linked to the saved order
                                Flux.fromIterable(orderItems)
                                    .doOnNext(item -> item.setOrder(savedOrder)) // Ensure item has the saved order's ID
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
        return orderRepository.findByUserId(userId);
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
     * Deletes an order by its ID.
     * This will also cascade delete associated OrderItems if configured in your entity mapping.
     * If not, you might need to manually delete OrderItems first.
     *
     * @param id The ID of the order to delete.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteOrderById(Long id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + id)))
                .flatMap(order ->
                    // First delete associated order items to avoid foreign key constraints
                    orderItemRepository.deleteByOrder(order) 
                                      .then(orderRepository.delete(order)) // Then delete the order itself
                )
                .then(); // Ensure it returns Mono<Void>
    }

    /**
     * Deletes all orders associated with a specific user ID.
     * This will first delete all order items belonging to those orders,
     * then delete the orders themselves.
     *
     * @param userId The ID of the user whose orders are to be deleted.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId) // Find all orders for the user
                .flatMap(order ->
                    // For each order, first delete its items
                    orderItemRepository.deleteByOrder(order) // Assumes deleteByOrder(Order order)
                        .then(Mono.just(order)) // Pass the order along
                )
                .collectList() // Collect all orders into a list
                .flatMap(orders ->
                    // After all items are deleted, delete the orders themselves
                    orderRepository.deleteAll(orders) // Use deleteAll with a collection
                )
                .then(); // Ensure it returns Mono<Void>
    }

    /**
     * Deletes a specific order item from an order.
     *
     * @param orderId The ID of the order the item belongs to.
     * @param orderItemId The ID of the order item to delete.
     * @return A Mono<Void> indicating completion, or Mono.error(ResourceNotFoundException) if not found.
     */
    public Mono<Void> deleteOrderItem(Long orderId, Long orderItemId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + orderId)))
                .flatMap(order ->
                    orderItemRepository.findById(orderItemId)
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order Item not found with ID: " + orderItemId + " in order " + orderId)))
                            .flatMap(orderItem -> {
                                if (!orderItem.getOrder().getId().equals(order.getId())) {
                                    return Mono.error(new IllegalArgumentException("Order item " + orderItemId + " does not belong to order " + orderId));
                                }
                                // Before deleting, you might want to return stock (if needed)
                                return orderItemRepository.delete(orderItem)
                                        .doOnSuccess(v -> {
                                            // Optional: Update the order's total amount and save it if you want to reflect changes immediately
                                            // This requires re-fetching items or maintaining a collection
                                            // For simplicity, we're just deleting the item here.
                                            // If Order has a collection of OrderItems and is eagerly loaded, update it
                                            order.getOrderItems().remove(orderItem);
                                            order.setTotalAmount(order.getTotalAmount().subtract(orderItem.getPriceAtTimeOfOrder().multiply(BigDecimal.valueOf(orderItem.getQuantity()))));
                                            orderRepository.save(order).subscribe(); // Non-blocking fire-and-forget for order update
                                        })
                                        .then(); // Return Mono<Void> after deletion
                            })
                );
    }

    /**
     * Clears all orders from the system.
     * Use with extreme caution, typically only for testing or specific reset scenarios.
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
}