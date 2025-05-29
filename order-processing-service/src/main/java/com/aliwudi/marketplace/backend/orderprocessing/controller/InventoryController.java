package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.orderprocessing.dto.InventoryUpdateRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockOperationRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockResponse;
import com.aliwudi.marketplace.backend.orderprocessing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public Mono<ResponseEntity<StockResponse>> getAvailableStock(@PathVariable String productId) {
        // Service method now returns Mono<Integer>
        return inventoryService.getAvailableStock(productId)
                .map(availableQuantity -> ResponseEntity.ok(StockResponse.builder()
                        .productId(productId)
                        .availableQuantity(availableQuantity)
                        .build()))
                .defaultIfEmpty(ResponseEntity.notFound().build()); // Handle case where stock might not be found
    }

    @PostMapping("/add-or-update")
    // For void methods in reactive controllers, you can return Mono<Void> or Mono<ResponseEntity<Void>>.
    // If you return Mono<Void>, Spring WebFlux will automatically send a 200 OK or 201 Created if annotated.
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> createOrUpdateInventory(@RequestBody InventoryUpdateRequest request) {
        // Service method now returns Mono<Void>
        return inventoryService.createOrUpdateInventory(request.getProductId(), request.getQuantity());
    }

    // Endpoint for reserving stock (e.g., when an order is placed)
    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> reserveStock(@RequestBody StockOperationRequest request) {
        // Service method now returns Mono<Void>
        return inventoryService.reserveStock(request.getProductId(), request.getQuantity());
    }

    // Endpoint for releasing stock (e.g., if an order is cancelled or payment fails)
    @PostMapping("/release")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> releaseStock(@RequestBody StockOperationRequest request) {
        // Service method now returns Mono<Void>
        return inventoryService.releaseStock(request.getProductId(), request.getQuantity());
    }

    // Endpoint for confirming reservation and deducting stock (e.g., after successful payment)
    @PostMapping("/confirm-deduct")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> confirmReservationAndDeductStock(@RequestBody StockOperationRequest request) {
        // Service method now returns Mono<Void>
        return inventoryService.confirmReservationAndDeductStock(request.getProductId(), request.getQuantity());
    }
}