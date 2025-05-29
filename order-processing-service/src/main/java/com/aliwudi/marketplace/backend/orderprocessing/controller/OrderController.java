// src/main/java/com/marketplace/emarketplacebackend/controller/OrderController.java
package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.orderprocessing.model.Order;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderStatus;
import com.aliwudi.marketplace.backend.orderprocessing.service.OrderService;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;

import com.aliwudi.marketplace.backend.orderprocessing.dto.CheckoutRequest; // For CheckoutRequest DTO
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import lombok.Data; // For CheckoutRequest DTO
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createOrder(@RequestBody CheckoutRequest checkoutRequest) {
        try {
            Order newOrder = orderService.createOrder(
                checkoutRequest.getUserId(),
                checkoutRequest.getItems(),
                checkoutRequest.getShippingAddress(),
                checkoutRequest.getPaymentMethod()
            );
            return new ResponseEntity<>(newOrder, HttpStatus.CREATED);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (InsufficientStockException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred during order creation: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return new ResponseEntity<>(orders, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(order -> new ResponseEntity<>(order, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        try {
            List<Order> orders = orderService.getOrdersByUserId(userId);
            return new ResponseEntity<>(orders, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        try {
            String statusString = statusUpdate.get("newStatus");
            if (statusString == null || statusString.trim().isEmpty()) {
                return new ResponseEntity<>("Missing or empty 'newStatus' in request body", HttpStatus.BAD_REQUEST);
            }
            OrderStatus newStatus = OrderStatus.valueOf(statusString.toUpperCase());
            Order updatedOrder = orderService.updateOrderStatus(id, newStatus);
            return new ResponseEntity<>(updatedOrder, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>("Invalid OrderStatus value: " + statusUpdate.get("newStatus"), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred updating order status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}