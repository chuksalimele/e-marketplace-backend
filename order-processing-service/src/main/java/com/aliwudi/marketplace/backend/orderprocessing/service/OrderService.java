// src/main/java/com/marketplace/emarketplacebackend/service/OrderService.java
package com.aliwudi.marketplace.backend.orderprocessing.service;


import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderStatus;

import com.aliwudi.marketplace.backend.orderprocessing.model.Order;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderItem;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.OrderItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;

import lombok.Data; // For OrderItemRequest DTO
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Autowired
    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        UserRepository userRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Order createOrder(Long userId, List<OrderItemRequest> itemRequests, String shippingAddress, String paymentMethod) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Order order = new Order();
        order.setUser(user);
        order.setShippingAddress(shippingAddress);
        order.setPaymentMethod(paymentMethod);
        // orderDate and orderStatus are set by @PrePersist in Order entity
        
        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest request : itemRequests) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));

            if (product.getStockQuantity() < request.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product: " + product.getName() +
                                                   ". Available: " + product.getStockQuantity() + ", Requested: " + request.getQuantity());
            }

            product.decreaseStock(request.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(request.getQuantity());
            orderItem.setPriceAtTimeOfOrder(product.getPrice());
            order.addOrderItem(orderItem); // Use the helper method to link item to order

            totalOrderAmount = totalOrderAmount.add(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        }

        order.setTotalAmount(totalOrderAmount);

        Order savedOrder = orderRepository.save(order);
        return savedOrder;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public List<Order> getOrdersByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        return orderRepository.findByUser(user);
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        order.setOrderStatus(newStatus);
        return orderRepository.save(order);
    }

    @Data // Generates getters and setters for this DTO
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        private Long productId;
        private Integer quantity;
    }
}