package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InventoryNotFoundException;
import com.aliwudi.marketplace.backend.common.model.Inventory;
import com.aliwudi.marketplace.backend.orderprocessing.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux for methods returning multiple items
import org.springframework.data.domain.Pageable; // Import for pagination


@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /**
     * Retrieves the available stock quantity for a given product ID.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @return A Mono emitting the available quantity.
     * @throws InventoryNotFoundException if inventory for the product is not found.
     */
    @Transactional(readOnly = true)
    public Mono<Integer> getAvailableStock(Long productId) {
        log.info("Checking available stock for product: {}", productId);
        return Mono.just(productId) // Convert String to Long
                .flatMap(longProductId -> inventoryRepository.findByProductId(longProductId))
                .switchIfEmpty(Mono.error(new InventoryNotFoundException("Inventory not found for product: " + productId)))
                .map(Inventory::getAvailableQuantity)
                .doOnSuccess(quantity -> log.info("Available stock for product {}: {}", productId, quantity));
    }

    /**
     * Creates a new inventory record or updates an existing one for a given product ID.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @param quantity The new available quantity.
     * @return A Mono<Void> indicating completion.
     */
    @Transactional
    public Mono<Void> createOrUpdateInventory(Long productId, Integer quantity) {
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
                        .then();
    }

    /**
     * Reserves a specified quantity of stock for a product.
     * Decreases available quantity and increases reserved quantity.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @param quantityToReserve The quantity to reserve.
     * @return A Mono<Void> indicating completion.
     * @throws InventoryNotFoundException if inventory for the product is not found.
     * @throws InsufficientStockException if there's insufficient stock.
     */
    @Transactional
    public Mono<Void> reserveStock(Long productId, Integer quantityToReserve) {
        log.info("Attempting to reserve {} units for product: {}", quantityToReserve, productId);
        return  inventoryRepository.findByProductId(productId)
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
                        .then();
    }

    /**
     * Releases a specified quantity of reserved stock for a product.
     * Decreases reserved quantity and increases available quantity.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @param quantityToRelease The quantity to release.
     * @return A Mono<Void> indicating completion.
     * @throws InventoryNotFoundException if inventory for the product is not found.
     * @throws IllegalArgumentException if attempting to release more than reserved quantity.
     */
    @Transactional
    public Mono<Void> releaseStock(Long productId, Integer quantityToRelease) {
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
                        .then();
    }

    /**
     * Confirms a reservation and permanently deducts stock from reserved quantity.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @param quantityConfirmed The quantity to confirm and deduct.
     * @return A Mono<Void> indicating completion.
     * @throws InventoryNotFoundException if inventory for the product is not found.
     * @throws IllegalArgumentException if attempting to confirm more than reserved quantity.
     */
    @Transactional
    public Mono<Void> confirmReservationAndDeductStock(Long productId, Integer quantityConfirmed) {
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
                        .then();
    }

    // --- NEW: InventoryRepository Implementations ---

    /**
     * Finds all inventory records with pagination.
     *
     * @param pageable Pagination information.
     * @return A Flux of Inventory records.
     */
    @Transactional(readOnly = true)
    public Flux<Inventory> findAllInventory(Pageable pageable) {
        log.info("Finding all inventory with pagination: {}", pageable);
        return inventoryRepository.findAllBy(pageable);
    }

    /**
     * Finds inventory records where available quantity is greater than a specified threshold, with pagination.
     *
     * @param quantity The minimum available quantity.
     * @param pageable Pagination information.
     * @return A Flux of Inventory records.
     */
    @Transactional(readOnly = true)
    public Flux<Inventory> findInventoryByAvailableQuantityGreaterThan(Integer quantity, Pageable pageable) {
        log.info("Finding inventory with available quantity greater than {} with pagination: {}", quantity, pageable);
        return inventoryRepository.findByAvailableQuantityGreaterThan(quantity, pageable);
    }

    /**
     * Decrements the available quantity for a product directly in the database.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @param quantity The amount to decrement.
     * @return A Mono emitting the number of rows updated.
     */
    @Transactional
    public Mono<Integer> decrementAvailableQuantity(Long productId, Integer quantity) {
        log.info("Attempting to decrement available quantity for product {} by {}", productId, quantity);
        return inventoryRepository.decrementAvailableQuantity(productId, quantity)
                .doOnSuccess(rows -> log.info("Decremented available quantity for product {}. Rows updated: {}", productId, rows))
                .doOnError(throwable -> log.error("Failed to decrement available quantity for product {}: {}", productId, throwable.getMessage()));
    }

    /**
     * Increments the available quantity for a product directly in the database.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @param quantity The amount to increment.
     * @return A Mono emitting the number of rows updated.
     */
    @Transactional
    public Mono<Integer> incrementAvailableQuantity(Long productId, Integer quantity) {
        log.info("Attempting to increment available quantity for product {} by {}", productId, quantity);
        return inventoryRepository.incrementAvailableQuantity(productId, quantity)
                .doOnSuccess(rows -> log.info("Incremented available quantity for product {}. Rows updated: {}", productId, rows))
                .doOnError(throwable -> log.error("Failed to increment available quantity for product {}: {}", productId, throwable.getMessage()));
    }

    /**
     * Updates the reserved quantity for a product directly in the database.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @param reservedQuantity The new reserved quantity.
     * @return A Mono emitting the number of rows updated.
     */
    @Transactional
    public Mono<Integer> updateReservedQuantity(Long productId, Integer reservedQuantity) {
        log.info("Attempting to update reserved quantity for product {} to {}", productId, reservedQuantity);
        return inventoryRepository.updateReservedQuantity(productId, reservedQuantity)
                .doOnSuccess(rows -> log.info("Updated reserved quantity for product {}. Rows updated: {}", productId, rows))
                .doOnError(throwable -> log.error("Failed to update reserved quantity for product {}: {}", productId, throwable.getMessage()));
    }

    /**
     * Counts all inventory records.
     *
     * @return A Mono emitting the total count of inventory records.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countAllInventory() {
        log.info("Counting all inventory records.");
        return inventoryRepository.count()
                .doOnSuccess(count -> log.info("Total inventory records: {}", count))
                .doOnError(throwable -> log.error("Failed to count all inventory records: {}", throwable.getMessage()));
    }

    /**
     * Counts inventory records where available quantity is greater than a specified threshold.
     *
     * @param quantity The minimum available quantity.
     * @return A Mono emitting the count.
     */
    @Transactional(readOnly = true)
    public Mono<Long> countInventoryByAvailableQuantityGreaterThan(Integer quantity) {
        log.info("Counting inventory records with available quantity greater than {}", quantity);
        return inventoryRepository.countByAvailableQuantityGreaterThan(quantity)
                .doOnSuccess(count -> log.info("Inventory records with available quantity > {}: {}", quantity, count))
                .doOnError(throwable -> log.error("Failed to count inventory records with available quantity greater than {}: {}", quantity, throwable.getMessage()));
    }

    /**
     * Checks if an inventory record exists for a given product ID.
     * Converts String productId to Long for repository interaction.
     *
     * @param productId The ID of the product (as String).
     * @return A Mono emitting true if it exists, false otherwise.
     */
    @Transactional(readOnly = true)
    public Mono<Boolean> existsInventoryByProductId(Long productId) {
        log.info("Checking if inventory exists for product: {}", productId);
        return inventoryRepository.existsByProductId(productId)
                .doOnSuccess(exists -> log.info("Inventory for product {} exists: {}", productId, exists))
                .doOnError(throwable -> log.error("Failed to check existence of inventory for product {}: {}", productId, throwable.getMessage()));
    }
}
