package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.model.Order;
import com.aliwudi.marketplace.backend.common.model.OrderItem;
import com.aliwudi.marketplace.backend.orderprocessing.service.OrderService;
import com.aliwudi.marketplace.backend.orderprocessing.dto.CheckoutRequest;
import com.aliwudi.marketplace.backend.common.status.OrderStatus; // Import OrderStatus
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages; // For consistent messages
import com.aliwudi.marketplace.backend.orderprocessing.service.OrderService.OrderItemRequest; // Import inner DTO

import jakarta.validation.Valid; // For @Valid
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map; // Still used for status update payload

// Static import for API path constants
import static com.aliwudi.marketplace.backend.common.constants.ApiPaths.*;

@RestController
@RequestMapping(ORDER_CONTROLLER_BASE) // MODIFIED
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    // Removed direct injection of integration services as they are used within the service layer.

    /**
     * Endpoint to create a new order for a user based on checkout request.
     *
     * @param checkoutRequest The CheckoutRequest DTO containing user ID, items, shipping address, and payment method.
     * @return A Mono emitting the created Order.
     */
    @PostMapping(ORDER_CHECKOUT) // MODIFIED
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created for resource creation
    public Mono<Order> createOrder(@Valid @RequestBody CheckoutRequest checkoutRequest) {
        // userId should ideally come from authenticated principal in API Gateway
        // For now, assuming it's part of the request payload or handled by API Gateway context.
        return orderService.createOrder(
                checkoutRequest.getUserId(),
                checkoutRequest.getItems(),
                checkoutRequest.getShippingAddress(),
                checkoutRequest.getPaymentMethod()
        );
        // Exceptions (ResourceNotFoundException, InsufficientStockException, IllegalArgumentException)
        // are now handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all orders with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping(ORDER_GET_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Order> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findAllOrders(pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve an order by its ID.
     *
     * @param id The ID of the order.
     * @return A Mono emitting the Order.
     */
    @GetMapping(ORDER_GET_BY_ID) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
        // Exceptions (ResourceNotFoundException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve orders by a specific user ID with pagination.
     *
     * @param userId The ID of the user.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping(ORDER_GET_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Order> getOrdersByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrdersByUserId(userId, pageable);
        // Exceptions (ResourceNotFoundException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update the status of an existing order.
     *
     * @param id The ID of the order to update.
     * @param statusUpdate A map containing the "newStatus" string.
     * @return A Mono emitting the updated Order.
     * @throws IllegalArgumentException if the status string is invalid or missing.
     */
    @PutMapping(ORDER_UPDATE_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Order> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        String statusString = statusUpdate.get("newStatus");
        if (statusString == null || statusString.trim().isEmpty()) {
            throw new IllegalArgumentException(ApiResponseMessages.MISSING_NEW_STATUS_BAD_REQUEST);
        }

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(statusString.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ORDER_STATUS_VALUE + statusString);
        }

        return orderService.updateOrderStatus(id, newStatus);
        // Exceptions (ResourceNotFoundException, IllegalArgumentException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete an order by its ID.
     *
     * @param id The ID of the order to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     */
    @DeleteMapping(ORDER_DELETE_BY_ID) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content for successful deletion
    public Mono<Void> deleteOrderById(@PathVariable Long id) {
        return orderService.deleteOrderById(id);
        // Exceptions (ResourceNotFoundException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete all orders by a specific user ID.
     *
     * @param userId The ID of the user whose orders are to be deleted.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     */
    @DeleteMapping(ORDER_DELETE_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteOrdersByUserId(@PathVariable Long userId) {
        return orderService.deleteOrdersByUserId(userId);
        // Exceptions (ResourceNotFoundException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a specific order item from an order.
     *
     * @param orderId The ID of the order the item belongs to.
     * @param orderItemId The ID of the order item to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     */
    @DeleteMapping(ORDER_DELETE_ITEM) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long orderItemId
    ) {
        return orderService.deleteOrderItem(orderId, orderItemId);
        // Exceptions (ResourceNotFoundException, IllegalArgumentException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to clear all orders from the system. Use with extreme caution.
     *
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     */
    @DeleteMapping(ORDER_CLEAR_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> clearAllOrders() {
        return orderService.clearAllOrders();
        // Errors are handled by GlobalExceptionHandler.
    }

    // --- OrderItem Controller Endpoints ---

    /**
     * Endpoint to retrieve all order items with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of OrderItem.
     */
    @GetMapping(ORDER_ITEMS_GET_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<OrderItem> getAllOrderItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findAllOrderItems(pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all order items belonging to a specific order with pagination.
     *
     * @param orderId The ID of the order.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of OrderItem.
     */
    @GetMapping(ORDER_ITEMS_GET_BY_ORDER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<OrderItem> getOrderItemsByOrderId(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrderItemsByOrderId(orderId, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all order items containing a specific product with pagination.
     *
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of OrderItem.
     */
    @GetMapping(ORDER_ITEMS_GET_BY_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<OrderItem> getOrderItemsByProductId(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrderItemsByProductId(productId, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find a specific order item by order ID and product ID.
     *
     * @param orderId The ID of the order.
     * @param productId The ID of the product.
     * @return A Mono emitting the OrderItem.
     */
    @GetMapping(ORDER_ITEMS_FIND_SPECIFIC) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<OrderItem> findSpecificOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long productId) {
        return orderService.findSpecificOrderItem(orderId, productId);
        // Exceptions (ResourceNotFoundException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all order items.
     *
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(ORDER_ITEMS_COUNT_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllOrderItems() {
        return orderService.countAllOrderItems();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all order items for a specific order.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(ORDER_ITEMS_COUNT_BY_ORDER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countOrderItemsByOrderId(@PathVariable Long orderId) {
        return orderService.countOrderItemsByOrderId(orderId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all order items for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(ORDER_ITEMS_COUNT_BY_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countOrderItemsByProductId(@PathVariable Long productId) {
        return orderService.countOrderItemsByProductId(productId);
        // Errors are handled by GlobalExceptionHandler.
    }

      
    /**
     * Endpoint to check if a specific product exists within a specific order.
     *
     * @param orderId The ID of the order.
     * @return A Mono emitting true if it exists, false otherwise (Boolean).
     */
    @GetMapping(ORDER_CHECK_EXISTS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> checkOrderExists(@PathVariable Long orderId) {
        return orderService.checkOrderExists(orderId);
        // Errors are handled by GlobalExceptionHandler.
    }
  
    
    /**
     * Endpoint to check if a specific product exists within a specific order.
     *
     * @param orderId The ID of the order.
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise (Boolean).
     */
    @GetMapping(ORDER_ITEMS_CHECK_EXISTS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> checkOrderItemExists(
            @PathVariable Long orderId,
            @PathVariable Long productId) {
        return orderService.checkOrderItemExists(orderId, productId);
        // Errors are handled by GlobalExceptionHandler.
    }

    // --- NEW: Order Repository Controller Endpoints ---

    /**
     * Endpoint to retrieve all orders with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping(ORDER_ADMIN_GET_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Order> getAllOrdersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findAllOrders(pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find orders placed by a specific user with pagination.
     *
     * @param userId The ID of the user.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping(ORDER_ADMIN_GET_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Order> getOrdersByUserIdPaginated(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrdersByUserId(userId, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find orders by their current status with pagination.
     *
     * @param status The status of the order.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping(ORDER_ADMIN_GET_BY_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
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
            throw new IllegalArgumentException("Invalid order status: " + status);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrdersByStatus(orderStatus, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find orders placed within a specific time range with pagination.
     *
     * @param startTime The start time of the range (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @param endTime The end time of the range (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping(ORDER_ADMIN_GET_BY_TIME_RANGE) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Order> getOrdersByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        LocalDateTime start;
        LocalDateTime end;
        try {
            start = LocalDateTime.parse(startTime);
            end = LocalDateTime.parse(endTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");
        }

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrdersByTimeRange(start, end, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find orders by a specific user and status with pagination.
     *
     * @param userId The ID of the user.
     * @param status The status of the order.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Order.
     */
    @GetMapping(ORDER_ADMIN_GET_BY_USER_AND_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
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
            throw new IllegalArgumentException("Invalid order status: " + status);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return orderService.findOrdersByUserIdAndStatus(userId, orderStatus, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all orders.
     *
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(ORDER_COUNT_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllOrders() {
        return orderService.countAllOrders();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count orders placed by a specific user.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(ORDER_COUNT_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countOrdersByUserId(@PathVariable Long userId) {
        return orderService.countOrdersByUserId(userId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count orders by their current status.
     *
     * @param status The status of the order.
     * @return A Mono emitting the count (Long).
     * @throws IllegalArgumentException if the status string is invalid.
     */
    @GetMapping(ORDER_COUNT_BY_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countOrdersByStatus(@PathVariable String status) {
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ORDER_STATUS_VALUE + status);
        }
        return orderService.countOrdersByStatus(orderStatus);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count orders placed within a specific time range.
     *
     * @param startTime The start time of the range (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @param endTime The end time of the range (ISO 8601 format:YYYY-MM-ddTHH:mm:ss).
     * @return A Mono emitting the count (Long).
     * @throws IllegalArgumentException if the date format is invalid.
     */
    @GetMapping(ORDER_COUNT_BY_TIME_RANGE) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countOrdersByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            return orderService.countOrdersByTimeRange(start, end);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");
        }
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count orders by a specific user and status.
     *
     * @param userId The ID of the user.
     * @param status The status of the order.
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(ORDER_COUNT_BY_USER_AND_STATUS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countOrdersByUserIdAndStatus(
            @PathVariable Long userId,
            @PathVariable String status) {
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ORDER_STATUS_VALUE + status);
        }
        return orderService.countOrdersByUserIdAndStatus(userId, orderStatus);
        // Errors are handled by GlobalExceptionHandler.
    }
}