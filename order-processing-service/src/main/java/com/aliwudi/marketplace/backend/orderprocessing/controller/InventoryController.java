package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.orderprocessing.dto.InventoryUpdateRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockOperationRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockResponse; // Assuming this DTO exists
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException; // Assuming this exception
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException; // Assuming this exception
import com.aliwudi.marketplace.backend.orderprocessing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public Mono<StandardResponseEntity> getAvailableStock(@PathVariable String productId) {
        return inventoryService.getAvailableStock(productId) // Service returns Mono<Integer>
                .map(availableQuantity -> StandardResponseEntity.ok(StockResponse.builder()
                                .productId(productId)
                                .availableQuantity(availableQuantity)
                                .build(),
                        ApiResponseMessages.OPERATION_SUCCESSFUL // Or a more specific message if available
                ))
                // Handle cases where getAvailableStock might return Mono.empty() or throw an exception
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + productId
                )))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage()
                        )));
    }

    @PostMapping("/add-or-update")
    public Mono<StandardResponseEntity> createOrUpdateInventory(@RequestBody InventoryUpdateRequest request) {
        return inventoryService.createOrUpdateInventory(request.getProductId(), request.getQuantity())
                .then(Mono.just(StandardResponseEntity.created(null, // No specific data payload for void operations
                        ApiResponseMessages.OPERATION_SUCCESSFUL // Or a more specific message like "Inventory updated/created."
                )))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage()
                        )));
    }

    @PostMapping("/reserve")
    public Mono<StandardResponseEntity> reserveStock(@RequestBody StockOperationRequest request) {
        return inventoryService.reserveStock(request.getProductId(), request.getQuantity())
                .then(Mono.just(StandardResponseEntity.ok(null,
                        ApiResponseMessages.OPERATION_SUCCESSFUL // Or "Stock reserved successfully."
                )))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + request.getProductId()
                        )))
                .onErrorResume(InsufficientStockException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage() // Assuming exception message provides details
                        )))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage()
                        )));
    }

    @PostMapping("/release")
    public Mono<StandardResponseEntity> releaseStock(@RequestBody StockOperationRequest request) {
        return inventoryService.releaseStock(request.getProductId(), request.getQuantity())
                .then(Mono.just(StandardResponseEntity.ok(null,
                        ApiResponseMessages.OPERATION_SUCCESSFUL // Or "Stock released successfully."
                )))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + request.getProductId()
                        )))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage()
                        )));
    }

    @PostMapping("/confirm-deduct")
    public Mono<StandardResponseEntity> confirmReservationAndDeductStock(@RequestBody StockOperationRequest request) {
        return inventoryService.confirmReservationAndDeductStock(request.getProductId(), request.getQuantity())
                .then(Mono.just(StandardResponseEntity.ok(null,
                        ApiResponseMessages.OPERATION_SUCCESSFUL // Or "Stock confirmed and deducted successfully."
                )))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + request.getProductId()
                        )))
                .onErrorResume(InsufficientStockException.class, e ->
                        Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage()
                        )))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.GENERAL_SERVER_ERROR + ": " + e.getMessage()
                        )));
    }
}