package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.StoreRequest;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidStoreDataException;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Store;
import com.aliwudi.marketplace.backend.product.repository.StoreRepository; // Assuming StoreRepository
import com.aliwudi.marketplace.backend.user.repository.UserRepository; // Assuming UserRepository for sellerId validation
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages; // For consistent exception messages

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StoreService{

    private final StoreRepository storeRepository;
    private final UserRepository userRepository; // To validate sellerId

    
    public Mono<Store> createStore(StoreRequest storeRequest) {
        // Validate seller existence
        Mono<Boolean> sellerExistsMono = userRepository.existsById(storeRequest.getSellerId())
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + storeRequest.getSellerId())));

        // Check for duplicate store name (optional, but good practice if names should be unique)
        Mono<Boolean> duplicateNameCheckMono = storeRepository.findByName(storeRequest.getName())
                .flatMap(existingStore -> Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_STORE_NAME)))
                .hasElement() // Check if any element is emitted (i.e., store found)
                .map(found -> !found); // Map to true if not found (i.e., no duplicate)

        return Mono.zip(sellerExistsMono, duplicateNameCheckMono)
                .flatMap(tuple -> {
                    // If we reach here, seller exists and name is unique
                    Store store = Store.builder()
                            .name(storeRequest.getName())
                            .description(storeRequest.getDescription())
                            .address(storeRequest.getAddress())
                            .sellerId(storeRequest.getSellerId())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return storeRepository.save(store);
                })
                .onErrorResume(e -> {
                    if (e instanceof ResourceNotFoundException || e instanceof DuplicateResourceException) {
                        return Mono.error(e); // Re-throw specific, already handled exceptions
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_CREATING_STORE, e)); // Generic error
                });
    }

    
    public Mono<Store> updateStore(Long id, StoreRequest storeRequest) {
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .flatMap(existingStore -> {
                    // Update only allowed fields
                    if (storeRequest.getName() != null && !storeRequest.getName().isBlank()) {
                        existingStore.setName(storeRequest.getName());
                    }
                    if (storeRequest.getDescription() != null && !storeRequest.getDescription().isBlank()) {
                        existingStore.setDescription(storeRequest.getDescription());
                    }
                    if (storeRequest.getAddress() != null && !storeRequest.getAddress().isBlank()) {
                        existingStore.setAddress(storeRequest.getAddress());
                    }
                    // Seller ID should generally not be updated after creation, or requires specific logic/permissions
                    // if (storeRequest.getSellerId() != null) {
                    //    existingStore.setSellerId(storeRequest.getSellerId());
                    // }
                    existingStore.setUpdatedAt(LocalDateTime.now());
                    return storeRepository.save(existingStore);
                });
    }

    
    public Mono<Void> deleteStore(Long id) {
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .flatMap(storeRepository::delete);
    }

    
    public Mono<Store> getStoreById(Long id) {
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)));
    }

    
    public Flux<Store> getAllStores() {
        return storeRepository.findAll();
    }

    
    public Flux<Store> getStoresBySeller(Long sellerId, Long offset, Integer limit) {
        // Optional: Validate seller existence here if you want to throw 404 for non-existent seller
        return userRepository.existsById(sellerId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + sellerId)))
                .flatMapMany(exists -> storeRepository.findBySeller_Id(sellerId, offset, limit));
    }

    
    public Mono<Long> countStoresBySeller(Long sellerId) {
         // Optional: Validate seller existence here if you want to throw 404 for non-existent seller
        return userRepository.existsById(sellerId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + sellerId)))
                .flatMap(exists -> storeRepository.countBySeller_Id(sellerId));
    }
}