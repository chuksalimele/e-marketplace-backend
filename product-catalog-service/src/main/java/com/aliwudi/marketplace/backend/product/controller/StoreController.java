package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.common.dto.StoreDto;
import com.aliwudi.marketplace.backend.product.dto.StoreRequest;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidStoreDataException;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Store;
import com.aliwudi.marketplace.backend.product.service.StoreService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /**
     * Helper method to map Store entity to StoreDto DTO for public exposure.
     */
    private StoreDto mapStoreToStoreDto(Store store) {
        if (store == null) {
            return null;
        }
        return StoreDto.builder()
                .id(store.getId())
                .name(store.getName())
                .description(store.getDescription())
                .phoneNumber(store.getPhoneNumber())
                .address(store.getAddress())
                .sellerId(store.getSellerId())
                .locationId(store.getLocationId()) // Include locationId in response
                .rating(store.getRating()) // Include rating in response
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> createStore(@Valid @RequestBody StoreRequest storeRequest) {
        // Basic input validation
        if (storeRequest.getName() == null || storeRequest.getName().isBlank() ||
            storeRequest.getSellerId() == null || storeRequest.getSellerId() <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_CREATION_REQUEST));
        }

        return storeService.createStore(storeRequest)
                .map(createdStore -> (StandardResponseEntity) StandardResponseEntity.created(mapStoreToStoreDto(createdStore), ApiResponseMessages.STORE_CREATED_SUCCESS))
                .onErrorResume(DuplicateResourceException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage()))) // Use e.getMessage() for more specific duplicate error
                .onErrorResume(InvalidStoreDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage()))) // User not found
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CREATING_STORE + ": " + e.getMessage())));
    }

    @GetMapping
    public Mono<StandardResponseEntity> getAllStores(
            @RequestParam(defaultValue = "0") int page, // Changed from Long offset to int page
            @RequestParam(defaultValue = "20") int size) { // Changed from Integer limit to int size

        if (page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return storeService.getAllStores(page, size) // Updated method call
                .map(this::mapStoreToStoreDto)
                .collectList()
                .map(storeResponses -> (StandardResponseEntity) StandardResponseEntity.ok(storeResponses, ApiResponseMessages.STORES_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORES + ": " + e.getMessage())));
    }

    @GetMapping("/count")
    public Mono<StandardResponseEntity> countAllStores() {
        return storeService.countAllStores()
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.STORE_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORE_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/{id}")
    public Mono<StandardResponseEntity> getStoreById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_ID));
        }

        return storeService.getStoreById(id)
                .map(store -> (StandardResponseEntity) StandardResponseEntity.ok(mapStoreToStoreDto(store), ApiResponseMessages.STORE_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> // Catch specific exception
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORE + ": " + e.getMessage())));
    }

    @GetMapping("/by-seller/{sellerId}")
    public Mono<StandardResponseEntity> getStoresBySeller(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") int page, // Changed from Long offset to int page
            @RequestParam(defaultValue = "20") int size) { // Changed from Integer limit to int size

        if (sellerId == null || sellerId <= 0 || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return storeService.getStoresBySeller(sellerId, page, size) // Updated method call
                .map(this::mapStoreToStoreDto)
                .collectList()
                .map(storeResponses -> (StandardResponseEntity) StandardResponseEntity.ok(storeResponses, ApiResponseMessages.STORES_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage()))) // User not found
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORES_BY_SELLER + ": " + e.getMessage())));
    }

    @GetMapping("/by-seller/{sellerId}/count")
    public Mono<StandardResponseEntity> countStoresBySeller(@PathVariable Long sellerId) {
        if (sellerId == null || sellerId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_USER_ID));
        }

        return storeService.countStoresBySeller(sellerId)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.STORE_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage()))) // User not found
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORE_COUNT_BY_SELLER + ": " + e.getMessage())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> updateStore(@PathVariable Long id, @Valid @RequestBody StoreRequest storeRequest) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_ID));
        }
        // Additional validation for update request can be added here if needed, e.g., name cannot be blank

        return storeService.updateStore(id, storeRequest)
                .map(updatedStore -> (StandardResponseEntity) StandardResponseEntity.ok(mapStoreToStoreDto(updatedStore), ApiResponseMessages.STORE_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .onErrorResume(DuplicateResourceException.class, e -> // Catch specific duplicate error for update
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(InvalidStoreDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_STORE + ": " + e.getMessage())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> deleteStore(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_ID));
        }

        return storeService.deleteStore(id)
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.STORE_DELETED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_STORE + ": " + e.getMessage())));
    }

    // --- New Endpoints based on StoreRepository methods ---

    @GetMapping("/search/name")
    public Mono<StandardResponseEntity> searchStoresByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (name == null || name.isBlank() || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }

        return storeService.searchStoresByName(name, page, size)
                .map(this::mapStoreToStoreDto)
                .collectList()
                .map(storeResponses -> (StandardResponseEntity) StandardResponseEntity.ok(storeResponses, ApiResponseMessages.STORES_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_STORES + ": " + e.getMessage())));
    }

    @GetMapping("/search/name/count")
    public Mono<StandardResponseEntity> countSearchStoresByName(@RequestParam String name) {
        if (name == null || name.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }

        return storeService.countSearchStoresByName(name)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.STORE_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_STORE_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/search/locationId")
    public Mono<StandardResponseEntity> searchStoresByLocationId(
            @RequestParam String locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (locationId == null || locationId.isBlank() || page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }

        return storeService.searchStoresByLocationId(locationId, page, size)
                .map(this::mapStoreToStoreDto)
                .collectList()
                .map(storeResponses -> (StandardResponseEntity) StandardResponseEntity.ok(storeResponses, ApiResponseMessages.STORES_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_STORES + ": " + e.getMessage())));
    }

    @GetMapping("/search/locationId/count")
    public Mono<StandardResponseEntity> countSearchStoresByLocationId(@RequestParam String locationId) {
        if (locationId == null || locationId.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }

        return storeService.countSearchStoresByLocationId(locationId)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.STORE_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_STORE_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/min-rating/{minRating}")
    public Mono<StandardResponseEntity> getStoresByMinRating(
            @PathVariable Double minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        if (minRating == null || minRating < 0.0 || minRating > 5.0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_RATING_RANGE));
        }
        
        return storeService.getStoresByMinRating(minRating, page, size)
                .map(this::mapStoreToStoreDto)
                .collectList()
                .map(storeResponses -> (StandardResponseEntity) StandardResponseEntity.ok(storeResponses, ApiResponseMessages.STORES_RETRIEVED_SUCCESS))
                .onErrorResume(InvalidStoreDataException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORES + ": " + e.getMessage())));
    }

    @GetMapping("/min-rating/{minRating}/count")
    public Mono<StandardResponseEntity> countStoresByMinRating(@PathVariable Double minRating) {
        if (minRating == null || minRating < 0.0 || minRating > 5.0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PARAMETERS));
        }

        return storeService.countStoresByMinRating(minRating)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.STORE_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(InvalidStoreDataException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORE_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/exists/name-and-seller")
    public Mono<StandardResponseEntity> checkStoreExistsByNameAndSeller(
            @RequestParam String name,
            @RequestParam Long sellerId) {

        if (name == null || name.isBlank() || sellerId == null || sellerId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PARAMETERS));
        }

        return storeService.existsStoreByNameAndSeller(name, sellerId)
                .map(exists -> {
                    Map<String, Boolean> data = Map.of("exists", exists);
                    return (StandardResponseEntity) StandardResponseEntity.ok(data, ApiResponseMessages.STORE_EXISTS_CHECK_SUCCESS);
                })
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CHECKING_STORE_EXISTENCE + ": " + e.getMessage())));
    }
}