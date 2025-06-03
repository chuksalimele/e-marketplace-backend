package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.orderprocessing.service.OrderService;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.dto.CheckoutRequest;
import com.aliwudi.marketplace.backend.common.model.Order; // Import Order model
import com.aliwudi.marketplace.backend.common.model.OrderItem; // Import OrderItem model

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux
import java.util.Map;
import lombok.RequiredArgsConstructor;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.status.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;


@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

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
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + e.getMessage())))
                .onErrorResume(InsufficientStockException.class, e
                        -> Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DURING_ORDER_CREATION + ": " + e.getMessage())));
    }

    @GetMapping
    public Mono<StandardResponseEntity> getAllOrders() {
        return orderService.getAllOrders()
                .collectList()
                .map(orders -> StandardResponseEntity.ok(orders, ApiResponseMessages.ORDERS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ORDERS + ": " + e.getMessage())));
    }

    @GetMapping("/{id}")
    public Mono<StandardResponseEntity> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(order -> StandardResponseEntity.ok(order, ApiResponseMessages.ORDER_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ORDERS + ": " + e.getMessage())));
    }

    @GetMapping("/user/{userId}")
    public Mono<StandardResponseEntity> getOrdersByUserId(@PathVariable Long userId) {
        return orderService.getOrdersByUserId(userId)
                .collectList()
                .map(orders -> StandardResponseEntity.ok(orders, ApiResponseMessages.ORDERS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + userId)))
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

        return orderService.updateOrderStatus(id, newStatus)
                .map(updatedOrder -> StandardResponseEntity.ok(updatedOrder, ApiResponseMessages.ORDER_STATUS_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e
                        -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_ORDER_STATUS + ": " + e.getMessage())));
    }

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
                .map(aVoid -> StandardResponseEntity.ok(null, ApiResponseMessages.ORDERS_DELETED_SUCCESS_FOR_USER)) // Specific message
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
                .onErrorResume(IllegalArgumentException.class, e
                        -> Mono.just(StandardResponseEntity.badRequest(e.getMessage()))) // For item not belonging to order
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

    // --- NEW: OrderItem Controller Endpoints ---

    /**
     * Endpoint to retrieve all order items with pagination.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of OrderItem.
     */
    @GetMapping("/items/all")
    public Flux<OrderItem> getAllOrderItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findAllOrderItems(pageable);
    }

    /**
     * Endpoint to retrieve all order items belonging to a specific order with pagination.
     * @param orderId The ID of the order.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of OrderItem.
     */
    @GetMapping("/items/byOrder/{orderId}")
    public Flux<OrderItem> getOrderItemsByOrderId(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrderItemsByOrderId(orderId, pageable);
    }

    /**
     * Endpoint to retrieve all order items containing a specific product with pagination.
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of OrderItem.
     */
    @GetMapping("/items/byProduct/{productId}")
    public Flux<OrderItem> getOrderItemsByProductId(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrderItemsByProductId(productId, pageable);
    }

    /**
     * Endpoint to find a specific order item by order ID and product ID.
     * @param orderId The ID of the order.
     * @param productId The ID of the product.
     * @return A Mono emitting the OrderItem.
     */
    @GetMapping("/items/find/{orderId}/{productId}")
    public Mono<StandardResponseEntity> findSpecificOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long productId) {
        return orderService.findSpecificOrderItem(orderId, productId)
                .map(orderItem -> StandardResponseEntity.ok(orderItem, ApiResponseMessages.ORDER_ITEM_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ORDER_ITEM_NOT_FOUND + " for order " + orderId + " and product " + productId)))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ORDER_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all order items.
     * @return A Mono emitting the count.
     */
    @GetMapping("/items/count/all")
    public Mono<StandardResponseEntity> countAllOrderItems() {
        return orderService.countAllOrderItems()
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ORDER_ITEM_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ORDER_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all order items for a specific order.
     * @param orderId The ID of the order.
     * @return A Mono emitting the count.
     */
    @GetMapping("/items/count/byOrder/{orderId}")
    public Mono<StandardResponseEntity> countOrderItemsByOrderId(@PathVariable Long orderId) {
        return orderService.countOrderItemsByOrderId(orderId)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ORDER_ITEM_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ORDER_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all order items for a specific product.
     * @param productId The ID of the product.
     * @return A Mono emitting the count.
     */
    @GetMapping("/items/count/byProduct/{productId}")
    public Mono<StandardResponseEntity> countOrderItemsByProductId(@PathVariable Long productId) {
        return orderService.countOrderItemsByProductId(productId)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ORDER_ITEM_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ORDER_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to check if a specific product exists within a specific order.
     * @param orderId The ID of the order.
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    @GetMapping("/items/exists/{orderId}/{productId}")
    public Mono<StandardResponseEntity> checkOrderItemExists(
            @PathVariable Long orderId,
            @PathVariable Long productId) {
        return orderService.checkOrderItemExists(orderId, productId)
                .map(exists -> StandardResponseEntity.ok(exists, ApiResponseMessages.ORDER_ITEM_EXISTS_CHECK_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CHECKING_ORDER_ITEM_EXISTENCE + ": " + e.getMessage())));
    }

    // --- NEW: Order Repository Controller Endpoints ---

    /**
     * Endpoint to retrieve all orders with pagination.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping("/admin/all")
    public Flux<Order> getAllOrdersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findAllOrders(pageable);
    }

    /**
     * Endpoint to find orders placed by a specific user with pagination.
     * @param userId The ID of the user.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping("/admin/byUser/{userId}")
    public Flux<Order> getOrdersByUserIdPaginated(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrdersByUserId(userId, pageable);
    }

    /**
     * Endpoint to find orders by their current status with pagination.
     * @param status The status of the order.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping("/admin/byStatus/{status}")
    public Flux<Order> getOrdersByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Handle invalid status input
            return Flux.error(new IllegalArgumentException("Invalid order status: " + status));
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrdersByStatus(orderStatus, pageable);
    }

    /**
     * Endpoint to find orders placed within a specific time range with pagination.
     * @param startTime The start time of the range (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @param endTime The end time of the range (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping("/admin/byTimeRange")
    public Flux<Order> getOrdersByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            return orderService.findOrdersByTimeRange(start, end, pageable);
        } catch (DateTimeParseException e) {
            return Flux.error(new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss."));
        }
    }

    /**
     * Endpoint to find orders by a specific user and status with pagination.
     * @param userId The ID of the user.
     * @param status The status of the order.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping("/admin/byUserAndStatus/{userId}/{status}")
    public Flux<Order> getOrdersByUserIdAndStatus(
            @PathVariable Long userId,
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Flux.error(new IllegalArgumentException("Invalid order status: " + status));
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrdersByUserIdAndStatus(userId, orderStatus, pageable);
    }

    /**
     * Endpoint to count all orders.
     * @return A Mono emitting the count.
     */
    @GetMapping("/count/all")
    public Mono<StandardResponseEntity> countAllOrders() {
        return orderService.countAllOrders()
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ORDER_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ORDERS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count orders placed by a specific user.
     * @param userId The ID of the user.
     * @return A Mono emitting the count.
     */
    @GetMapping("/count/byUser/{userId}")
    public Mono<StandardResponseEntity> countOrdersByUserId(@PathVariable Long userId) {
        return orderService.countOrdersByUserId(userId)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ORDER_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ORDERS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count orders by their current status.
     * @param status The status of the order.
     * @return A Mono emitting the count.
     */
    @GetMapping("/count/byStatus/{status}")
    public Mono<StandardResponseEntity> countOrdersByStatus(@PathVariable String status) {
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ORDER_STATUS_VALUE + status));
        }
        return orderService.countOrdersByStatus(orderStatus)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ORDER_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ORDERS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count orders placed within a specific time range.
     * @param startTime The start time of the range (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @param endTime The end time of the range (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @return A Mono emitting the count.
     */
    @GetMapping("/count/byTimeRange")
    public Mono<StandardResponseEntity> countOrdersByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            return orderService.countOrdersByTimeRange(start, end)
                    .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ORDER_COUNT_SUCCESS))
                    .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ORDERS + ": " + e.getMessage())));
        } catch (DateTimeParseException e) {
            return Mono.just(StandardResponseEntity.badRequest("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss."));
        }
    }

    /**
     * Endpoint to count orders by a specific user and status.
     * @param userId The ID of the user.
     * @param status The status of the order.
     * @return A Mono emitting the count.
     */
    @GetMapping("/count/byUserAndStatus/{userId}/{status}")
    public Mono<StandardResponseEntity> countOrdersByUserIdAndStatus(
            @PathVariable Long userId,
            @PathVariable String status) {
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ORDER_STATUS_VALUE + status));
        }
        return orderService.countOrdersByUserIdAndStatus(userId, orderStatus)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ORDER_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ORDERS + ": " + e.getMessage())));
    }
}
