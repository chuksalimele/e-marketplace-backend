package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.model.Inventory;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.orderprocessing.dto.InventoryUpdateRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockOperationRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockResponse;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux for methods returning multiple items
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InventoryNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;


@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public Mono<StandardResponseEntity> getAvailableStock(@PathVariable String productId) {
        return inventoryService.getAvailableStock(productId)
                .map(availableQuantity -> StandardResponseEntity.ok(StockResponse.builder()
                                .productId(productId)
                                .availableQuantity(availableQuantity)
                                .build(),
                        ApiResponseMessages.OPERATION_SUCCESSFUL
                ))
                .onErrorResume(InventoryNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + productId)))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    @PostMapping("/add-or-update")
    public Mono<StandardResponseEntity> createOrUpdateInventory(@RequestBody InventoryUpdateRequest request) {
        return inventoryService.createOrUpdateInventory(request.getProductId(), request.getQuantity())
                .then(Mono.just(StandardResponseEntity.created(null,
                        ApiResponseMessages.OPERATION_SUCCESSFUL
                )))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + request.getProductId())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    @PostMapping("/reserve")
    public Mono<StandardResponseEntity> reserveStock(@RequestBody StockOperationRequest request) {
        return inventoryService.reserveStock(request.getProductId(), request.getQuantity())
                .then(Mono.just(StandardResponseEntity.ok(null,
                        ApiResponseMessages.OPERATION_SUCCESSFUL
                )))
                .onErrorResume(InventoryNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + request.getProductId())))
                .onErrorResume(InsufficientStockException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + request.getProductId())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    @PostMapping("/release")
    public Mono<StandardResponseEntity> releaseStock(@RequestBody StockOperationRequest request) {
        return inventoryService.releaseStock(request.getProductId(), request.getQuantity())
                .then(Mono.just(StandardResponseEntity.ok(null,
                        ApiResponseMessages.OPERATION_SUCCESSFUL
                )))
                .onErrorResume(InventoryNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + request.getProductId())))
                .onErrorResume(IllegalArgumentException.class, e -> // For "Cannot release more than reserved"
                        Mono.just(StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + request.getProductId())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    @PostMapping("/confirm-deduct")
    public Mono<StandardResponseEntity> confirmReservationAndDeductStock(@RequestBody StockOperationRequest request) {
        return inventoryService.confirmReservationAndDeductStock(request.getProductId(), request.getQuantity())
                .then(Mono.just(StandardResponseEntity.ok(null,
                        ApiResponseMessages.OPERATION_SUCCESSFUL
                )))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + request.getProductId())))
                .onErrorResume(InsufficientStockException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
                .onErrorResume(IllegalArgumentException.class, e -> // For "Cannot confirm more than reserved"
                        Mono.just(StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + request.getProductId())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    // --- NEW: InventoryRepository Controller Endpoints ---

    /**
     * Endpoint to retrieve all inventory records with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Inventory records.
     */
    @GetMapping("/admin/all")
    public Flux<Inventory> getAllInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return inventoryService.findAllInventory(pageable);
    }

    /**
     * Endpoint to find inventory records where available quantity is greater than a specified threshold, with pagination.
     *
     * @param quantity The minimum available quantity.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Inventory records.
     */
    @GetMapping("/admin/availableGreaterThan/{quantity}")
    public Flux<Inventory> getInventoryByAvailableQuantityGreaterThan(
            @PathVariable Integer quantity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return inventoryService.findInventoryByAvailableQuantityGreaterThan(quantity, pageable);
    }

    /**
     * Endpoint to decrement the available quantity for a product directly.
     *
     * @param request A StockOperationRequest containing productId (String) and quantity.
     * @return A Mono emitting StandardResponseEntity.
     */
    @PostMapping("/admin/decrement")
    public Mono<StandardResponseEntity> decrementAvailableQuantity(@RequestBody StockOperationRequest request) {
        if (request.getProductId() == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            return Mono.just(StandardResponseEntity.badRequest("Invalid request: productId and positive quantity are required."));
        }
        return inventoryService.decrementAvailableQuantity(request.getProductId(), request.getQuantity())
                .map(rowsUpdated -> StandardResponseEntity.ok(rowsUpdated, "Available quantity decremented. Rows updated: " + rowsUpdated))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + request.getProductId())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to increment the available quantity for a product directly.
     *
     * @param request A StockOperationRequest containing productId (String) and quantity.
     * @return A Mono emitting StandardResponseEntity.
     */
    @PostMapping("/admin/increment")
    public Mono<StandardResponseEntity> incrementAvailableQuantity(@RequestBody StockOperationRequest request) {
        if (request.getProductId() == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            return Mono.just(StandardResponseEntity.badRequest("Invalid request: productId and positive quantity are required."));
        }
        return inventoryService.incrementAvailableQuantity(request.getProductId(), request.getQuantity())
                .map(rowsUpdated -> StandardResponseEntity.ok(rowsUpdated, "Available quantity incremented. Rows updated: " + rowsUpdated))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + request.getProductId())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to update the reserved quantity for a product directly.
     *
     * @param request A StockOperationRequest containing productId (String) and quantity (for reservedQuantity).
     * @return A Mono emitting StandardResponseEntity.
     */
    @PutMapping("/admin/updateReserved")
    public Mono<StandardResponseEntity> updateReservedQuantity(@RequestBody StockOperationRequest request) {
        if (request.getProductId() == null || request.getQuantity() == null || request.getQuantity() < 0) {
            return Mono.just(StandardResponseEntity.badRequest("Invalid request: productId and non-negative quantity are required."));
        }
        return inventoryService.updateReservedQuantity(request.getProductId(), request.getQuantity())
                .map(rowsUpdated -> StandardResponseEntity.ok(rowsUpdated, "Reserved quantity updated. Rows updated: " + rowsUpdated))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + request.getProductId())))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all inventory records.
     *
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/all")
    public Mono<StandardResponseEntity> countAllInventory() {
        return inventoryService.countAllInventory()
                .map(count -> StandardResponseEntity.ok(count, "Total inventory records counted."))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count inventory records with available quantity greater than a threshold.
     *
     * @param quantity The minimum available quantity.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/availableGreaterThan/{quantity}")
    public Mono<StandardResponseEntity> countInventoryByAvailableQuantityGreaterThan(@PathVariable Integer quantity) {
        return inventoryService.countInventoryByAvailableQuantityGreaterThan(quantity)
                .map(count -> StandardResponseEntity.ok(count, "Inventory records with available quantity greater than " + quantity + " counted."))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }

    /**
     * Endpoint to check if an inventory record exists for a given product ID.
     *
     * @param productId The ID of the product (as String).
     * @return A Mono emitting StandardResponseEntity with a boolean indicating existence.
     */
    @GetMapping("/exists/{productId}")
    public Mono<StandardResponseEntity> existsInventoryByProductId(@PathVariable String productId) {
        return inventoryService.existsInventoryByProductId(productId)
                .map(exists -> StandardResponseEntity.ok(exists, "Inventory existence check for product " + productId + " completed."))
                .onErrorResume(NumberFormatException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest("Invalid Product ID format: " + productId)))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage())));
    }
}