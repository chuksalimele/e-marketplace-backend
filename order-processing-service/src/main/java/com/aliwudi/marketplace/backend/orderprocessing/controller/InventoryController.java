package com.aliwudi.marketplace.backend.orderprocessing.controller;


import com.aliwudi.marketplace.backend.orderprocessing.dto.InventoryUpdateRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockOperationRequest;
import com.aliwudi.marketplace.backend.orderprocessing.dto.StockResponse;
import com.aliwudi.marketplace.backend.orderprocessing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getAvailableStock(@PathVariable String productId) {
        Integer availableQuantity = inventoryService.getAvailableStock(productId);
        return ResponseEntity.ok(StockResponse.builder()
                .productId(productId)
                .availableQuantity(availableQuantity)
                .build());
    }

    @PostMapping("/add-or-update")
    @ResponseStatus(HttpStatus.CREATED)
    public void createOrUpdateInventory(@RequestBody InventoryUpdateRequest request) {
        inventoryService.createOrUpdateInventory(request.getProductId(), request.getQuantity());
    }

    // Endpoint for reserving stock (e.g., when an order is placed)
    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.OK)
    public void reserveStock(@RequestBody StockOperationRequest request) {
        inventoryService.reserveStock(request.getProductId(), request.getQuantity());
    }

    // Endpoint for releasing stock (e.g., if an order is cancelled or payment fails)
    @PostMapping("/release")
    @ResponseStatus(HttpStatus.OK)
    public void releaseStock(@RequestBody StockOperationRequest request) {
        inventoryService.releaseStock(request.getProductId(), request.getQuantity());
    }

    // Endpoint for confirming reservation and deducting stock (e.g., after successful payment)
    @PostMapping("/confirm-deduct")
    @ResponseStatus(HttpStatus.OK)
    public void confirmReservationAndDeductStock(@RequestBody StockOperationRequest request) {
        inventoryService.confirmReservationAndDeductStock(request.getProductId(), request.getQuantity());
    }
}