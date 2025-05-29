package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.orderprocessing.model.Order;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderStatus;
import com.aliwudi.marketplace.backend.orderprocessing.service.OrderService;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.dto.CheckoutRequest; // For CheckoutRequest DTO

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections

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
    public Mono<ResponseEntity<?>> createOrder(@RequestBody CheckoutRequest checkoutRequest) {
        return orderService.createOrder(
                checkoutRequest.getUserId(),
                checkoutRequest.getItems(),
                checkoutRequest.getShippingAddress(),
                checkoutRequest.getPaymentMethod()
        )
        .map(newOrder -> new ResponseEntity<>(newOrder, HttpStatus.CREATED))
        .onErrorResume(ResourceNotFoundException.class, e ->
            Mono.just(new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND)))
        .onErrorResume(InsufficientStockException.class, e ->
            Mono.just(new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST)))
        .onErrorResume(Exception.class, e ->
            Mono.just(new ResponseEntity<>("An error occurred during order creation: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    @GetMapping
    public Mono<ResponseEntity<List<Order>>> getAllOrders() {
        return orderService.getAllOrders() // Service returns Flux<Order>
                .collectList() // Collect Flux into a List<Order>
                .map(orders -> new ResponseEntity<>(orders, HttpStatus.OK));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Order>> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id) // Service returns Mono<Order>
                .map(order -> new ResponseEntity<>(order, HttpStatus.OK))
                .switchIfEmpty(Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND))); // Handle not found case reactively
    }

    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<List<Order>>> getOrdersByUserId(@PathVariable Long userId) {
        return orderService.getOrdersByUserId(userId) // Service returns Flux<Order>
                .collectList() // Collect Flux into a List<Order>
                .map(orders -> new ResponseEntity<>(orders, HttpStatus.OK))
                .onErrorResume(ResourceNotFoundException.class, e ->
                    Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND))); // Handle not found case reactively
    }

    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<?>> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        String statusString = statusUpdate.get("newStatus");
        if (statusString == null || statusString.trim().isEmpty()) {
            return Mono.just(new ResponseEntity<>("Missing or empty 'newStatus' in request body", HttpStatus.BAD_REQUEST));
        }

        // Use Mono.just() for simple value wrapping and flatMap for sequential reactive operations
        return Mono.just(statusString)
                .map(String::toUpperCase)
                .map(OrderStatus::valueOf) // Convert String to OrderStatus enum
                .onErrorResume(IllegalArgumentException.class, e -> // Handle invalid enum value
                    Mono.just(new ResponseEntity<>("Invalid OrderStatus value: " + statusUpdate.get("newStatus"), HttpStatus.BAD_REQUEST)))
                .flatMap(newStatus -> orderService.updateOrderStatus(id, newStatus)) // Service returns Mono<Order>
                .map(updatedOrder -> new ResponseEntity<>(updatedOrder, HttpStatus.OK))
                .onErrorResume(ResourceNotFoundException.class, e ->
                    Mono.just(new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND)))
                .onErrorResume(Exception.class, e -> // Catch any other unexpected exceptions
                    Mono.just(new ResponseEntity<>("An error occurred updating order status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR)));
    }
}