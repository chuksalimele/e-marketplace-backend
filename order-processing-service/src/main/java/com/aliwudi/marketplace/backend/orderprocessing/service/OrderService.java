package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderStatus;
import com.aliwudi.marketplace.backend.orderprocessing.model.Order;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderItem;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderItemRepository;
import com.aliwudi.marketplace.backend.product.model.Product; // Assuming this is the Product model
import com.aliwudi.marketplace.backend.product.repository.ProductRepository; // NEW: Import Reactive ProductRepository
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Keep for reactive transaction management
import reactor.core.publisher.Mono; // NEW: Import Mono for single reactive results
import reactor.core.publisher.Flux; // NEW: Import Flux for multiple reactive results

import java.math.BigDecimal;
import java.util.List;
// Remove Optional and ArrayList imports as Mono/Flux handle absence and collection creation

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository; // NEW: Inject ProductRepository

    @Autowired
    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository; // NEW: Initialize ProductRepository
    }

    @Transactional
    public Mono<Order> createOrder(Long userId, List<OrderItemRequest> itemRequests, String shippingAddress, String paymentMethod) {
        Order order = new Order();
        order.setUserId(userId);
        order.setShippingAddress(shippingAddress);
        order.setPaymentMethod(paymentMethod);
        // orderDate and orderStatus are set by @PrePersist in Order entity, or can be set here.

        // Process each item request reactively
        return Flux.fromIterable(itemRequests)
                .flatMap(request ->
                        productRepository.findById(request.getProductId()) // Assuming ProductRepository is reactive
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with ID: " + request.getProductId())))
                                .flatMap(product -> {
                                    if (product.getStockQuantity() < request.getQuantity()) {
                                        return Mono.error(new InsufficientStockException("Insufficient stock for product: " + product.getName() +
                                                ". Available: " + product.getStockQuantity() + ", Requested: " + request.getQuantity()));
                                    }
                                    // Decrease stock and save product reactively
                                    product.decreaseStock(request.getQuantity()); // Assuming decreaseStock updates the object
                                    return productRepository.save(product) // Save the updated product
                                            .map(savedProduct -> {
                                                // Create OrderItem
                                                OrderItem orderItem = new OrderItem();
                                                orderItem.setProduct(savedProduct); // Link to the updated product
                                                orderItem.setQuantity(request.getQuantity());
                                                orderItem.setPriceAtTimeOfOrder(savedProduct.getPrice());
                                                return orderItem;
                                            });
                                })
                )
                .collectList() // Collect all created OrderItems into a List
                .flatMap(orderItems -> {
                    // Calculate total amount
                    BigDecimal totalOrderAmount = orderItems.stream()
                            .map(item -> item.getPriceAtTimeOfOrder().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    order.setTotalAmount(totalOrderAmount);
                    order.setOrderItems(orderItems); // Set the collected items to the order

                    // Save the order. Assumes Order has cascade for OrderItems or OrderItemRepository is handled separately.
                    // If OrderItems need explicit saving, add orderItemRepository.saveAll(orderItems) here.
                    // For now, assuming cascade.
                    return orderRepository.save(order);
                });
    }

    public Flux<Order> getAllOrders() {
        return orderRepository.findAll(); // Returns Flux<Order>
    }

    public Mono<Order> getOrderById(Long id) {
        return orderRepository.findById(id) // Returns Mono<Order>
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + id)));
    }

    public Flux<Order> getOrdersByUserId(Long userId) {
        // Uncomment and make reactive if needed:
        // return userRepository.findById(userId)
        //         .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found with ID: " + userId)))
        //         .flatMapMany(user -> orderRepository.findByUserId(userId)); // Assuming findByUserId returns Flux
        return orderRepository.findByUserId(userId); // Assuming findByUserId returns Flux
    }

    @Transactional
    public Mono<Order> updateOrderStatus(Long orderId, OrderStatus newStatus) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found with ID: " + orderId)))
                .flatMap(order -> {
                    order.setOrderStatus(newStatus);
                    return orderRepository.save(order);
                });
    }

    @Data // Generates getters and setters for this DTO
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        private Long productId;
        private Integer quantity;
    }
}