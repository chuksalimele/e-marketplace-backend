package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.product.dto.StoreRequest;
import com.aliwudi.marketplace.backend.product.dto.StoreResponse; // New DTO for responses
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException; // Re-using or creating
import com.aliwudi.marketplace.backend.product.exception.InvalidStoreDataException; // New custom exception
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException; // Re-using or creating
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
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor // Using Lombok for constructor injection
public class StoreController {

    private final StoreService storeService;

    /**
     * Helper method to map Store entity to StoreResponse DTO for public exposure.
     */
    private StoreResponse mapStoreToStoreResponse(Store store) {
        if (store == null) {
            return null;
        }
        return StoreResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .description(store.getDescription())
                .address(store.getAddress())
                .sellerId(store.getSellerId())
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
                .map(createdStore -> (StandardResponseEntity) StandardResponseEntity.created(mapStoreToStoreResponse(createdStore), ApiResponseMessages.STORE_CREATED_SUCCESS))
                .onErrorResume(DuplicateResourceException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.conflict(ApiResponseMessages.DUPLICATE_STORE_NAME)))
                .onErrorResume(InvalidStoreDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.USER_NOT_FOUND + e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CREATING_STORE + ": " + e.getMessage())));
    }

    @GetMapping
    public Mono<StandardResponseEntity> getAllStores() {
        return storeService.getAllStores()
                .map(this::mapStoreToStoreResponse)
                .collectList()
                .map(storeResponses -> (StandardResponseEntity) StandardResponseEntity.ok(storeResponses, ApiResponseMessages.STORES_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORES + ": " + e.getMessage())));
    }

    @GetMapping("/{id}")
    public Mono<StandardResponseEntity> getStoreById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_ID));
        }

        return storeService.getStoreById(id)
                .map(store -> (StandardResponseEntity) StandardResponseEntity.ok(mapStoreToStoreResponse(store), ApiResponseMessages.STORE_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORE + ": " + e.getMessage())));
    }

    @GetMapping("/by-seller/{sellerId}")
    public Mono<StandardResponseEntity> getStoresBySeller(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (sellerId == null || sellerId <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_USER_ID));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return storeService.getStoresBySeller(sellerId, offset, limit)
                .map(this::mapStoreToStoreResponse)
                .collectList()
                .map(storeResponses -> (StandardResponseEntity) StandardResponseEntity.ok(storeResponses, ApiResponseMessages.STORES_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + sellerId)))
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
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + sellerId)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_STORE_COUNT_BY_SELLER + ": " + e.getMessage())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<StandardResponseEntity> updateStore(@PathVariable Long id, @Valid @RequestBody StoreRequest storeRequest) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_STORE_ID));
        }

        return storeService.updateStore(id, storeRequest)
                .map(updatedStore -> (StandardResponseEntity) StandardResponseEntity.ok(mapStoreToStoreResponse(updatedStore), ApiResponseMessages.STORE_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.STORE_NOT_FOUND + id)))
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
}