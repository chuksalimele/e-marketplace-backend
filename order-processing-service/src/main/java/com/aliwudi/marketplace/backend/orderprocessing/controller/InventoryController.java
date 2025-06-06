package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.model.Inventory;
import com.aliwudi.marketplace.backend.orderprocessing.dto.InventoryUpdateRequest; // Assuming this DTO exists
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockOperationRequest; // Assuming this DTO exists
import com.aliwudi.marketplace.backend.orderprocessing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid; // For @Valid annotation

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    // Removed ProductIntegrationService direct injection; it's now used within InventoryService for prepareDto.

    /**
     * Endpoint to retrieve the available stock quantity for a given product ID.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the available quantity (Integer).
     */
    @GetMapping("/{productId}/available-stock")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Integer> getAvailableStock(@PathVariable Long productId) {
        return inventoryService.getAvailableStock(productId);
        // Errors (InventoryNotFoundException, IllegalArgumentException from service) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to create a new inventory record or update an existing one for a given product ID.
     *
     * @param request The InventoryUpdateRequest containing productId and quantity.
     * @return A Mono emitting the saved Inventory.
     */
    @PostMapping("/add-or-update")
    @ResponseStatus(HttpStatus.OK) // Or HttpStatus.CREATED if always new
    public Mono<Inventory> createOrUpdateInventory(@Valid @RequestBody InventoryUpdateRequest request) {
        return inventoryService.createOrUpdateInventory(request.getProductId(), request.getQuantity());
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to reserve a specified quantity of stock for a product.
     *
     * @param request The StockOperationRequest containing productId and quantityToReserve.
     * @return A Mono<Void> indicating completion (results in 204 No Content).
     */
    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reserveStock(@Valid @RequestBody StockOperationRequest request) {
        return inventoryService.reserveStock(request.getProductId(), request.getQuantity());
        // Errors (InventoryNotFoundException, InsufficientStockException, IllegalArgumentException)
        // are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to release a specified quantity of reserved stock for a product.
     *
     * @param request The StockOperationRequest containing productId and quantityToRelease.
     * @return A Mono<Void> indicating completion (results in 204 No Content).
     */
    @PostMapping("/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> releaseStock(@Valid @RequestBody StockOperationRequest request) {
        return inventoryService.releaseStock(request.getProductId(), request.getQuantity());
        // Errors (InventoryNotFoundException, IllegalArgumentException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to confirm a reservation and permanently deduct stock from reserved quantity.
     *
     * @param request The StockOperationRequest containing productId and quantityConfirmed.
     * @return A Mono<Void> indicating completion (results in 204 No Content).
     */
    @PostMapping("/confirm-deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> confirmReservationAndDeductStock(@Valid @RequestBody StockOperationRequest request) {
        return inventoryService.confirmReservationAndDeductStock(request.getProductId(), request.getQuantity());
        // Errors (InventoryNotFoundException, IllegalArgumentException) are handled by GlobalExceptionHandler.
    }

    // --- Inventory Repository Controller Endpoints ---

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
    @ResponseStatus(HttpStatus.OK)
    public Flux<Inventory> getAllInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return inventoryService.findAllInventory(pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find inventory records where available quantity is greater
     * than a specified threshold, with pagination.
     *
     * @param quantity The minimum available quantity.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Inventory records.
     */
    @GetMapping("/admin/availableGreaterThan/{quantity}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Inventory> getInventoryByAvailableQuantityGreaterThan(
            @PathVariable Integer quantity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return inventoryService.findInventoryByAvailableQuantityGreaterThan(quantity, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to decrement the available quantity for a product directly.
     *
     * @param request A StockOperationRequest containing productId and quantity.
     * @return A Mono emitting the number of rows updated (Integer).
     */
    @PostMapping("/admin/decrement")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Integer> decrementAvailableQuantity(@Valid @RequestBody StockOperationRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for decrement operation.");
        }
        return inventoryService.decrementAvailableQuantity(request.getProductId(), request.getQuantity());
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to increment the available quantity for a product directly.
     *
     * @param request A StockOperationRequest containing productId and quantity.
     * @return A Mono emitting the number of rows updated (Integer).
     */
    @PostMapping("/admin/increment")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Integer> incrementAvailableQuantity(@Valid @RequestBody StockOperationRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for increment operation.");
        }
        return inventoryService.incrementAvailableQuantity(request.getProductId(), request.getQuantity());
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update the reserved quantity for a product directly.
     *
     * @param request A StockOperationRequest containing productId and quantity (for reservedQuantity).
     * @return A Mono emitting the number of rows updated (Integer).
     */
    @PutMapping("/admin/updateReserved")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Integer> updateReservedQuantity(@Valid @RequestBody StockOperationRequest request) {
        if (request.getQuantity() == null || request.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative for update reserved quantity operation.");
        }
        return inventoryService.updateReservedQuantity(request.getProductId(), request.getQuantity());
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all inventory records.
     *
     * @return A Mono emitting the total count (Long).
     */
    @GetMapping("/count/all")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllInventory() {
        return inventoryService.countAllInventory();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count inventory records with available quantity greater than
     * a threshold.
     *
     * @param quantity The minimum available quantity.
     * @return A Mono emitting the count (Long).
     */
    @GetMapping("/count/availableGreaterThan/{quantity}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countInventoryByAvailableQuantityGreaterThan(@PathVariable Integer quantity) {
        return inventoryService.countInventoryByAvailableQuantityGreaterThan(quantity);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to check if an inventory record exists for a given product ID.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise (Boolean).
     */
    @GetMapping("/exists/{productId}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsInventoryByProductId(@PathVariable Long productId) {
        return inventoryService.existsInventoryByProductId(productId);
        // Errors are handled by GlobalExceptionHandler.
    }
}
