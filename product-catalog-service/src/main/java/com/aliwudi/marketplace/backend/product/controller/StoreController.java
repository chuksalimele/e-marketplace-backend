package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.common.model.Store;
import com.aliwudi.marketplace.backend.product.dto.StoreRequest;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.InvalidStoreDataException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // Corrected package
import com.aliwudi.marketplace.backend.product.service.StoreService;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map; // Keep for Map.of

// Removed unused imports: com.aliwudi.marketplace.backend.common.model.Product, com.aliwudi.marketplace.backend.common.model.Seller,
// com.aliwudi.marketplace.backend.product.repository.ProductRepository, com.aliwudi.marketplace.backend.product.repository.SellerRepository,
// com.aliwudi.marketplace.backend.product.service.SellerService, java.util.List, java.util.stream.Collectors, org.springframework.data.domain.PageRequest, org.springframework.data.domain.Pageable
// as prepareDto logic moved to service

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /**
     * Endpoint to create a new store.
     *
     * @param storeRequest The DTO containing store creation data.
     * @return A Mono emitting the created Store.
     * @throws IllegalArgumentException if input validation fails.
     * @throws DuplicateResourceException if a store with the same name already exists for the seller.
     * @throws InvalidStoreDataException if provided data is invalid.
     * @throws ResourceNotFoundException if the seller is not found.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    @PreAuthorize("hasRole('admin') or hasRole('seller')")
    public Mono<Store> createStore(@Valid @RequestBody StoreRequest storeRequest) {
        // Basic input validation
        if (storeRequest.getName() == null || storeRequest.getName().isBlank()
                || storeRequest.getSellerId() == null || storeRequest.getSellerId() <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_STORE_CREATION_REQUEST);
        }
        return storeService.createStore(storeRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all stores with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting all stores.
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Flux<Store> getAllStores(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return storeService.getAllStores(page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all stores.
     *
     * @return A Mono emitting the total count of stores.
     */
    @GetMapping("/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllStores() {
        return storeService.countAllStores();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a store by its ID.
     *
     * @param id The ID of the store to retrieve.
     * @return A Mono emitting the Store.
     * @throws IllegalArgumentException if store ID is invalid.
     * @throws ResourceNotFoundException if the store is not found.
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Store> getStoreById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_STORE_ID);
        }
        return storeService.getStoreById(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all stores owned by a specific seller with pagination.
     *
     * @param sellerId The ID of the seller.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting stores owned by the specified seller.
     * @throws IllegalArgumentException if seller ID or pagination parameters are invalid.
     * @throws ResourceNotFoundException if the seller is not found.
     */
    @GetMapping("/by-seller/{sellerId}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Store> getStoresBySeller(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (sellerId == null || sellerId <= 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return storeService.getStoresBySeller(sellerId, page, size);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all stores owned by a specific seller.
     *
     * @param sellerId The ID of the seller.
     * @return A Mono emitting the total count of stores for the seller.
     * @throws IllegalArgumentException if seller ID is invalid.
     * @throws ResourceNotFoundException if the seller is not found.
     */
    @GetMapping("/by-seller/{sellerId}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countStoresBySeller(@PathVariable Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        return storeService.countStoresBySeller(sellerId);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update an existing store.
     *
     * @param id The ID of the store to update.
     * @param storeRequest The DTO containing store update data.
     * @return A Mono emitting the updated Store.
     * @throws IllegalArgumentException if store ID is invalid.
     * @throws ResourceNotFoundException if the store is not found.
     * @throws DuplicateResourceException if the updated name causes a duplicate.
     * @throws InvalidStoreDataException if provided data is invalid.
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin') or hasRole('seller')")
    public Mono<Store> updateStore(@PathVariable Long id, @Valid @RequestBody StoreRequest storeRequest) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_STORE_ID);
        }
        // Additional validation for update request can be added here if needed, e.g., name cannot be blank
        return storeService.updateStore(id, storeRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a store by its ID.
     *
     * @param id The ID of the store to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if store ID is invalid.
     * @throws ResourceNotFoundException if the store is not found.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('admin') or hasRole('seller')")
    public Mono<Void> deleteStore(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_STORE_ID);
        }
        return storeService.deleteStore(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    // --- New Endpoints based on StoreRepository methods ---

    /**
     * Endpoint to search stores by name (case-insensitive, contains) with pagination.
     *
     * @param name The search query for store name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching stores.
     * @throws IllegalArgumentException if search term or pagination parameters are invalid.
     */
    @GetMapping("/search/name")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Store> searchStoresByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (name == null || name.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return storeService.searchStoresByName(name, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count stores by name (case-insensitive, contains).
     *
     * @param name The search query for store name.
     * @return A Mono emitting the count of matching stores.
     * @throws IllegalArgumentException if search term is invalid.
     */
    @GetMapping("/search/name/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countSearchStoresByName(@RequestParam String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return storeService.countSearchStoresByName(name);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to search stores by location ID (case-insensitive, contains) with pagination.
     *
     * @param locationId The search query for location ID.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching stores.
     * @throws IllegalArgumentException if search term or pagination parameters are invalid.
     */
    @GetMapping("/search/locationId")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Store> searchStoresByLocationId(
            @RequestParam String locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (locationId == null || locationId.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return storeService.searchStoresByLocationId(locationId, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count stores by location ID (case-insensitive, contains).
     *
     * @param locationId The search query for location ID.
     * @return A Mono emitting the count of matching stores.
     * @throws IllegalArgumentException if search term is invalid.
     */
    @GetMapping("/search/locationId/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countSearchStoresByLocationId(@RequestParam String locationId) {
        if (locationId == null || locationId.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return storeService.countSearchStoresByLocationId(locationId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find stores with a rating greater than or equal to a minimum value, with pagination.
     *
     * @param minRating The minimum rating (inclusive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting filtered stores.
     * @throws IllegalArgumentException if minRating or pagination parameters are invalid.
     * @throws InvalidStoreDataException if minRating is out of range.
     */
    @GetMapping("/min-rating/{minRating}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<Store> getStoresByMinRating(
            @PathVariable Double minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (minRating == null || minRating < 0.0 || minRating > 5.0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return storeService.getStoresByMinRating(minRating, page, size);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count stores with a rating greater than or equal to a minimum value.
     *
     * @param minRating The minimum rating (inclusive).
     * @return A Mono emitting the count of filtered stores.
     * @throws IllegalArgumentException if minRating is invalid.
     * @throws InvalidStoreDataException if minRating is out of range.
     */
    @GetMapping("/min-rating/{minRating}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countStoresByMinRating(@PathVariable Double minRating) {
        if (minRating == null || minRating < 0.0 || minRating > 5.0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PARAMETERS);
        }
        return storeService.countStoresByMinRating(minRating);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to check if a store with a given name exists for a specific seller.
     *
     * @param name The name of the store.
     * @param sellerId The ID of the seller.
     * @return A Mono emitting true if a store with the name exists for the seller, false otherwise (Boolean).
     * @throws IllegalArgumentException if name or seller ID are invalid.
     */
    @GetMapping("/exists/name-and-seller")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> checkStoreExistsByNameAndSeller(
            @RequestParam String name,
            @RequestParam Long sellerId) {
        if (name == null || name.isBlank() || sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PARAMETERS);
        }
        return storeService.existsStoreByNameAndSeller(name, sellerId);
        // Errors are handled by GlobalExceptionHandler.
    }
}
