package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.StoreRequest;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidStoreDataException;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Store;
import com.aliwudi.marketplace.backend.product.repository.StoreRepository;
import com.aliwudi.marketplace.backend.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Import Sort for pagination
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@Service
@RequiredArgsConstructor
public class StoreService{

    private final StoreRepository storeRepository;
    private final UserRepository userRepository; // To validate sellerId

    /**
     * Creates a new store.
     * Validates seller existence and checks for duplicate store name for that seller.
     *
     * @param storeRequest The DTO containing store creation data.
     * @return A Mono emitting the created Store.
     */
    public Mono<Store> createStore(StoreRequest storeRequest) {
        // Validate seller existence
        Mono<Boolean> sellerExistsMono = userRepository.existsById(storeRequest.getSellerId())
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + storeRequest.getSellerId())));

        // Check for duplicate store name for this specific seller
        Mono<Boolean> duplicateNameCheckMono = storeRepository.existsByNameIgnoreCaseAndSellerId(storeRequest.getName(), storeRequest.getSellerId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_STORE_NAME_FOR_SELLER));
                    }
                    return Mono.just(true); // Name is unique for this seller
                });

        return Mono.zip(sellerExistsMono, duplicateNameCheckMono)
                .flatMap(tuple -> {
                    // If we reach here, seller exists and name is unique for that seller
                    Store store = Store.builder()
                            .name(storeRequest.getName())
                            .description(storeRequest.getDescription())
                            .address(storeRequest.getAddress())
                            .sellerId(storeRequest.getSellerId())
                            .locationId(storeRequest.getLocationId()) // Assuming locationId is part of StoreRequest
                            .rating(0.0) // Initialize rating
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return storeRepository.save(store);
                })
                .onErrorResume(e -> {
                    if (e instanceof ResourceNotFoundException || e instanceof DuplicateResourceException || e instanceof InvalidStoreDataException) {
                        return Mono.error(e); // Re-throw specific, already handled exceptions
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_CREATING_STORE + ": " + e.getMessage(), e)); // Generic error
                });
    }

    /**
     * Updates an existing store.
     * Finds the store by ID, updates its fields based on the request, and saves it.
     *
     * @param id The ID of the store to update.
     * @param storeRequest The DTO containing store update data.
     * @return A Mono emitting the updated Store.
     */
    public Mono<Store> updateStore(Long id, StoreRequest storeRequest) {
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .flatMap(existingStore -> {
                    // Update only allowed fields
                    if (storeRequest.getName() != null && !storeRequest.getName().isBlank()) {
                        // If name is being updated, check for uniqueness for the same seller (excluding current store)
                        return storeRepository.existsByNameIgnoreCaseAndSellerId(storeRequest.getName(), existingStore.getSellerId())
                                .flatMap(isDuplicate -> {
                                    if (isDuplicate && !existingStore.getName().equalsIgnoreCase(storeRequest.getName())) {
                                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_STORE_NAME_FOR_SELLER));
                                    }
                                    existingStore.setName(storeRequest.getName());
                                    return Mono.just(existingStore);
                                });
                    }
                    return Mono.just(existingStore);
                })
                .flatMap(existingStore -> {
                    if (storeRequest.getDescription() != null && !storeRequest.getDescription().isBlank()) {
                        existingStore.setDescription(storeRequest.getDescription());
                    }
                    if (storeRequest.getAddress() != null && !storeRequest.getAddress().isBlank()) {
                        existingStore.setAddress(storeRequest.getAddress());
                    }
                    if (storeRequest.getLocationId() != null) {
                        existingStore.setLocationId(storeRequest.getLocationId());
                    }
                    // Rating is typically updated via review aggregation, not directly
                    // if (storeRequest.getRating() != null) {
                    //     existingStore.setRating(storeRequest.getRating());
                    // }
                    existingStore.setUpdatedAt(LocalDateTime.now());
                    return storeRepository.save(existingStore);
                })
                .onErrorResume(e -> {
                    if (e instanceof ResourceNotFoundException || e instanceof DuplicateResourceException || e instanceof InvalidStoreDataException) {
                        return Mono.error(e); // Re-throw specific, already handled exceptions
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_UPDATING_STORE + ": " + e.getMessage(), e)); // Generic error
                });
    }

    /**
     * Deletes a store by its ID.
     *
     * @param id The ID of the store to delete.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteStore(Long id) {
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .flatMap(storeRepository::delete);
    }

    /**
     * Retrieves a store by its ID.
     *
     * @param id The ID of the store to retrieve.
     * @return A Mono emitting the Store if found, or an error if not.
     */
    public Mono<Store> getStoreById(Long id) {
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)));
    }

    /**
     * Retrieves all stores with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting all stores.
     */
    public Flux<Store> getAllStores(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return storeRepository.findAllBy(pageable);
    }

    /**
     * Counts all stores.
     *
     * @return A Mono emitting the total count of stores.
     */
    public Mono<Long> countAllStores() {
        return storeRepository.count();
    }

    /**
     * Finds all stores owned by a specific seller with pagination.
     *
     * @param sellerId The ID of the seller.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting stores owned by the specified seller.
     */
    public Flux<Store> getStoresBySeller(Long sellerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userRepository.existsById(sellerId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + sellerId)))
                .flatMapMany(exists -> storeRepository.findBySellerId(sellerId, pageable));
    }

    /**
     * Counts all stores owned by a specific seller.
     *
     * @param sellerId The ID of the seller.
     * @return A Mono emitting the total count of stores for the seller.
     */
    public Mono<Long> countStoresBySeller(Long sellerId) {
        return userRepository.existsById(sellerId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + sellerId)))
                .flatMap(exists -> storeRepository.countBySellerId(sellerId));
    }

    /**
     * Searches stores by name (case-insensitive, contains) with pagination.
     *
     * @param name The search query for store name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching stores.
     */
    public Flux<Store> searchStoresByName(String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return storeRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    /**
     * Counts stores by name (case-insensitive, contains).
     *
     * @param name The search query for store name.
     * @return A Mono emitting the count of matching stores.
     */
    public Mono<Long> countSearchStoresByName(String name) {
        return storeRepository.countByNameContainingIgnoreCase(name);
    }

    /**
     * Searches stores by locationId (case-insensitive, contains) with pagination.
     *
     * @param locationId The search query for location ID.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching stores.
     */
    public Flux<Store> searchStoresByLocationId(String locationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending()); // Assuming you want to sort by name
        return storeRepository.findByLocationIdContainingIgnoreCase(locationId, pageable);
    }

    /**
     * Counts stores by locationId (case-insensitive, contains).
     *
     * @param locationId The search query for location ID.
     * @return A Mono emitting the count of matching stores.
     */
    public Mono<Long> countSearchStoresByLocationId(String locationId) {
        return storeRepository.countByLocationIdContainingIgnoreCase(locationId);
    }

    /**
     * Finds stores with a rating greater than or equal to a minimum value, with pagination.
     *
     * @param minRating The minimum rating (inclusive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting filtered stores.
     */
    public Flux<Store> getStoresByMinRating(Double minRating, int page, int size) {
        if (minRating == null || minRating < 0.0 || minRating > 5.0) {
            return Mono.error(new InvalidStoreDataException(ApiResponseMessages.INVALID_RATING_RANGE));
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("rating").descending()); // Sort by rating descending
        return storeRepository.findByRatingGreaterThanEqual(minRating, pageable);
    }

    /**
     * Counts stores with a rating greater than or equal to a minimum value.
     *
     * @param minRating The minimum rating (inclusive).
     * @return A Mono emitting the count of filtered stores.
     */
    public Mono<Long> countStoresByMinRating(Double minRating) {
        if (minRating == null || minRating < 0.0 || minRating > 5.0) {
            return Mono.error(new InvalidStoreDataException(ApiResponseMessages.INVALID_RATING_RANGE));
        }
        return storeRepository.countByRatingGreaterThanEqual(minRating);
    }

    /**
     * Checks if a store with a given name exists for a specific seller.
     *
     * @param name The name of the store.
     * @param sellerId The ID of the seller.
     * @return A Mono emitting true if a store with the name exists for the seller, false otherwise.
     */
    public Mono<Boolean> existsStoreByNameAndSeller(String name, Long sellerId) {
        return storeRepository.existsByNameIgnoreCaseAndSellerId(name, sellerId);
    }
}