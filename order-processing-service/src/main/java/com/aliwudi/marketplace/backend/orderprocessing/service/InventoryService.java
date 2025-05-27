package com.aliwudi.marketplace.backend.orderprocessing.service;


import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InventoryNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.model.Inventory;
import com.aliwudi.marketplace.backend.orderprocessing.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public Integer getAvailableStock(String productId) {
        log.info("Checking available stock for product: {}", productId);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + productId));
        return inventory.getAvailableQuantity();
    }

    @Transactional
    public void createOrUpdateInventory(String productId, Integer quantity) {
        log.info("Creating or updating inventory for product: {} with quantity: {}", productId, quantity);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder()
                        .productId(productId)
                        .reservedQuantity(0) // Start with 0 reserved
                        .build());
        
        inventory.setAvailableQuantity(quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void reserveStock(String productId, Integer quantityToReserve) {
        log.info("Attempting to reserve {} units for product: {}", quantityToReserve, productId);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + productId));

        if (inventory.getAvailableQuantity() < quantityToReserve) {
            log.warn("Insufficient stock for product {}. Available: {}, Requested: {}",
                    productId, inventory.getAvailableQuantity(), quantityToReserve);
            throw new InsufficientStockException("Insufficient stock for product " + productId);
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantityToReserve);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantityToReserve);
        inventoryRepository.save(inventory);
        log.info("Successfully reserved {} units for product: {}. New available: {}, New reserved: {}",
                quantityToReserve, productId, inventory.getAvailableQuantity(), inventory.getReservedQuantity());
    }

    @Transactional
    public void releaseStock(String productId, Integer quantityToRelease) {
        log.info("Attempting to release {} units for product: {}", quantityToRelease, productId);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + productId));

        if (inventory.getReservedQuantity() < quantityToRelease) {
            // This should ideally not happen if the order logic is sound
            log.warn("Attempted to release more stock than reserved for product {}. Reserved: {}, Requested: {}",
                    productId, inventory.getReservedQuantity(), quantityToRelease);
             // Consider throwing an error or adjusting behavior based on your business rules
            throw new IllegalArgumentException("Cannot release more than reserved quantity for product " + productId);
        }
        
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantityToRelease);
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantityToRelease);
        inventoryRepository.save(inventory);
        log.info("Successfully released {} units for product: {}. New available: {}, New reserved: {}",
                quantityToRelease, productId, inventory.getAvailableQuantity(), inventory.getReservedQuantity());
    }

    @Transactional
    public void confirmReservationAndDeductStock(String productId, Integer quantityConfirmed) {
        log.info("Confirming reservation and deducting stock for product: {} with quantity: {}", productId, quantityConfirmed);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for product: " + productId));

        if (inventory.getReservedQuantity() < quantityConfirmed) {
            log.error("Attempted to confirm more stock than reserved for product {}. Reserved: {}, Confirmed: {}",
                    productId, inventory.getReservedQuantity(), quantityConfirmed);
            throw new IllegalArgumentException("Cannot confirm more than reserved quantity for product " + productId);
        }

        // When order is paid/confirmed, reserved stock becomes permanently unavailable
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantityConfirmed);
        // The available quantity was already reduced during reservation, so no change here
        inventoryRepository.save(inventory);
        log.info("Reservation confirmed and stock deducted for product: {}. New reserved: {}", productId, inventory.getReservedQuantity());
    }
}