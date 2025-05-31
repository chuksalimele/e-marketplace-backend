package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.orderprocessing.model.OrderStatus;
import com.aliwudi.marketplace.backend.orderprocessing.service.OrderService;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.dto.CheckoutRequest; // For CheckoutRequest DTO

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.Map;
import lombok.RequiredArgsConstructor; // NEW: Added for cleaner constructor injection
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor // Using Lombok for constructor injection
public class OrderController {

    private final OrderService orderService; // No @Autowired needed here

    @PostMapping("/checkout")
    public Mono<StandardResponseEntity> createOrder(@RequestBody CheckoutRequest checkoutRequest) {
        return orderService.createOrder(
                checkoutRequest.getUserId(),
                checkoutRequest.getItems(),
                checkoutRequest.getShippingAddress(),
                checkoutRequest.getPaymentMethod()
        )
                .map(newOrder -> StandardResponseEntity.created(newOrder, ApiResponseMessages.ORDER_CREATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + e.getMessage()))) // Concatenate ID/details from exception message
                .onErrorResume(InsufficientStockException.class, e
                        -> Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage()))) // Concatenate product details
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DURING_ORDER_CREATION + ": " + e.getMessage()))); // Append specific error message
    }

    @GetMapping
    public Mono<StandardResponseEntity> getAllOrders() {
        return orderService.getAllOrders() // Service returns Flux<Order>
                .collectList() // Collect Flux into a List<Order>
                .map(orders -> StandardResponseEntity.ok(orders, ApiResponseMessages.ORDERS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ORDERS + ": " + e.getMessage())));
    }

    @GetMapping("/{id}")
    public Mono<StandardResponseEntity> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id) // Service returns Mono<Order>
                // You could add .switchIfEmpty(Mono.error(new ResourceNotFoundException(...))) here
                // if the service doesn't throw it when not found, but returns Mono.empty()
                .map(order -> StandardResponseEntity.ok(order, ApiResponseMessages.ORDER_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + id))) // Use constant message + ID directly
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ORDERS + ": " + e.getMessage())));
    }

    @GetMapping("/user/{userId}")
    public Mono<StandardResponseEntity> getOrdersByUserId(@PathVariable Long userId) {
        return orderService.getOrdersByUserId(userId) // Service returns Flux<Order>
                .collectList() // Collect Flux into a List<Order>
                .map(orders -> StandardResponseEntity.ok(orders, ApiResponseMessages.ORDERS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + userId))) // Use constant message + ID directly
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ORDERS + ": " + e.getMessage())));
    }

    @PutMapping("/{id}/status")
    public Mono<StandardResponseEntity> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        String statusString = statusUpdate.get("newStatus");
        if (statusString == null || statusString.trim().isEmpty()) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.MISSING_NEW_STATUS_BAD_REQUEST));
        }

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(statusString.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ORDER_STATUS_VALUE + statusString));
        }

        return orderService.updateOrderStatus(id, newStatus) // Service returns Mono<Order>
                .map(updatedOrder -> StandardResponseEntity.ok(updatedOrder, ApiResponseMessages.ORDER_STATUS_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_ORDER_STATUS + ": " + e.getMessage())));
    }

    // NEW: Add the delete endpoints
    @DeleteMapping("/{id}")
    public Mono<StandardResponseEntity> deleteOrderById(@PathVariable Long id) {
        return orderService.deleteOrderById(id)
                .map(aVoid -> StandardResponseEntity.ok(null, ApiResponseMessages.ORDER_DELETED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_ORDER + ": " + e.getMessage())));
    }

    @DeleteMapping("/user/{userId}")
    public Mono<StandardResponseEntity> deleteOrdersByUserId(@PathVariable Long userId) {
        return orderService.deleteOrdersByUserId(userId)
                .map(aVoid -> StandardResponseEntity.ok(null, ApiResponseMessages.ORDERS_RETRIEVED_SUCCESS)) // Assuming this means all orders for user are deleted
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + userId)))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_ORDER + ": " + e.getMessage())));
    }

    @DeleteMapping("/order/{orderId}/item/{orderItemId}")
    public Mono<StandardResponseEntity> deleteOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long orderItemId
    ) {
        return orderService.deleteOrderItem(orderId, orderItemId)
                .map(aVoid -> StandardResponseEntity.ok(null, ApiResponseMessages.ORDER_ITEMS_DELETED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_ITEM_NOT_FOUND + orderItemId)))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_ORDER + ": " + e.getMessage())));
    }

    @DeleteMapping("/clearAll")
    public Mono<StandardResponseEntity> clearAllOrders() {
        return orderService.clearAllOrders()
                .map(aVoid -> StandardResponseEntity.ok(null, ApiResponseMessages.ALL_ORDERS_CLEARED_SUCCESS))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }
}
