package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InventoryNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.model.Inventory;
import com.aliwudi.marketplace.backend.orderprocessing.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Keep for reactive transaction management
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public Mono<Integer> getAvailableStock(String productId) {
        log.info("Checking available stock for product: {}", productId);
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException("Inventory not found for product: " + productId)))
                .map(Inventory::getAvailableQuantity)
                .doOnSuccess(quantity -> log.info("Available stock for product {}: {}", productId, quantity));
    }

    @Transactional
    public Mono<Void> createOrUpdateInventory(String productId, Integer quantity) {
        log.info("Creating or updating inventory for product: {} with quantity: {}", productId, quantity);
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.defer(() -> Mono.just(Inventory.builder()
                        .productId(productId)
                        .reservedQuantity(0) // Start with 0 reserved
                        .build())))
                .flatMap(inventory -> {
                    inventory.setAvailableQuantity(quantity);
                    return inventoryRepository.save(inventory);
                })
                .doOnSuccess(savedInventory -> log.info("Successfully created or updated inventory for product: {}", productId))
                .then(); // Convert Mono<Inventory> to Mono<Void>
    }

    @Transactional
    public Mono<Void> reserveStock(String productId, Integer quantityToReserve) {
        log.info("Attempting to reserve {} units for product: {}", quantityToReserve, productId);
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException("Inventory not found for product: " + productId)))
                .flatMap(inventory -> {
                    if (inventory.getAvailableQuantity() < quantityToReserve) {
                        log.warn("Insufficient stock for product {}. Available: {}, Requested: {}",
                                productId, inventory.getAvailableQuantity(), quantityToReserve);
                        return Mono.error(new InsufficientStockException("Insufficient stock for product " + productId));
                    }

                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantityToReserve);
                    inventory.setReservedQuantity(inventory.getReservedQuantity() + quantityToReserve);
                    return inventoryRepository.save(inventory);
                })
                .doOnSuccess(savedInventory -> log.info("Successfully reserved {} units for product: {}. New available: {}, New reserved: {}",
                        quantityToReserve, productId, savedInventory.getAvailableQuantity(), savedInventory.getReservedQuantity()))
                .doOnError(throwable -> log.error("Failed to reserve stock for product {}: {}", productId, throwable.getMessage()))
                .then(); // Convert Mono<Inventory> to Mono<Void>
    }

    @Transactional
    public Mono<Void> releaseStock(String productId, Integer quantityToRelease) {
        log.info("Attempting to release {} units for product: {}", quantityToRelease, productId);
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException("Inventory not found for product: " + productId)))
                .flatMap(inventory -> {
                    if (inventory.getReservedQuantity() < quantityToRelease) {
                        log.warn("Attempted to release more stock than reserved for product {}. Reserved: {}, Requested: {}",
                                productId, inventory.getReservedQuantity(), quantityToRelease);
                        return Mono.error(new IllegalArgumentException("Cannot release more than reserved quantity for product " + productId));
                    }

                    inventory.setReservedQuantity(inventory.getReservedQuantity() - quantityToRelease);
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantityToRelease);
                    return inventoryRepository.save(inventory);
                })
                .doOnSuccess(savedInventory -> log.info("Successfully released {} units for product: {}. New available: {}, New reserved: {}",
                        quantityToRelease, productId, savedInventory.getAvailableQuantity(), savedInventory.getReservedQuantity()))
                .doOnError(throwable -> log.error("Failed to release stock for product {}: {}", productId, throwable.getMessage()))
                .then(); // Convert Mono<Inventory> to Mono<Void>
    }

    @Transactional
    public Mono<Void> confirmReservationAndDeductStock(String productId, Integer quantityConfirmed) {
        log.info("Confirming reservation and deducting stock for product: {} with quantity: {}", productId, quantityConfirmed);
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException("Inventory not found for product: " + productId)))
                .flatMap(inventory -> {
                    if (inventory.getReservedQuantity() < quantityConfirmed) {
                        log.error("Attempted to confirm more stock than reserved for product {}. Reserved: {}, Confirmed: {}",
                                productId, inventory.getReservedQuantity(), quantityConfirmed);
                        return Mono.error(new IllegalArgumentException("Cannot confirm more than reserved quantity for product " + productId));
                    }

                    // When order is paid/confirmed, reserved stock becomes permanently unavailable
                    inventory.setReservedQuantity(inventory.getReservedQuantity() - quantityConfirmed);
                    // The available quantity was already reduced during reservation, so no change here
                    return inventoryRepository.save(inventory);
                })
                .doOnSuccess(savedInventory -> log.info("Reservation confirmed and stock deducted for product: {}. New reserved: {}", productId, savedInventory.getReservedQuantity()))
                .doOnError(throwable -> log.error("Failed to confirm reservation and deduct stock for product {}: {}", productId, throwable.getMessage()))
                .then(); // Convert Mono<Inventory> to Mono<Void>
    }
}