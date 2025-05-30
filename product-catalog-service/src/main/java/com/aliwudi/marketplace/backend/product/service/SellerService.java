package com.aliwudi.marketplace.backend.product.service; // Adjust package if 'seller' is not under 'product'

import com.aliwudi.marketplace.backend.product.dto.SellerRequest;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidSellerDataException;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Seller;
import com.aliwudi.marketplace.backend.product.repository.SellerRepository; // Assuming SellerRepository
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages; // For consistent exception messages

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SellerService{

    private final SellerRepository sellerRepository;

    
    public Mono<Seller> createSeller(SellerRequest sellerRequest) {
        // Check for duplicate email
        return sellerRepository.findByEmail(sellerRequest.getEmail())
                .flatMap(existingSeller -> Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_SELLER_EMAIL)))
                .switchIfEmpty(Mono.defer(() -> { // Only proceed if email is unique
                    Seller seller = Seller.builder()
                            .name(sellerRequest.getName())
                            .email(sellerRequest.getEmail())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return sellerRepository.save(seller);
                }))
                .cast(Seller.class) // Cast back to Seller as switchIfEmpty changes type to Mono<Object>
                .onErrorResume(e -> {
                    if (e instanceof DuplicateResourceException) {
                        return Mono.error(e); // Re-throw specific exception
                    }
                    return Mono.error(new InvalidSellerDataException(ApiResponseMessages.ERROR_CREATING_SELLER + ": " + e.getMessage()));
                });
    }

    
    public Mono<Seller> updateSeller(Long id, SellerRequest sellerRequest) {
        return sellerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .flatMap(existingSeller -> {
                    if (sellerRequest.getName() != null && !sellerRequest.getName().isBlank()) {
                        existingSeller.setName(sellerRequest.getName());
                    }
                    // Email usually not updatable or requires specific logic (e.g., re-verification)
                    // if (sellerRequest.getEmail() != null && !sellerRequest.getEmail().isBlank()) {
                    //     existingSeller.setEmail(sellerRequest.getEmail());
                    // }
                    existingSeller.setUpdatedAt(LocalDateTime.now());
                    return sellerRepository.save(existingSeller);
                })
                .onErrorResume(e -> {
                    if (e instanceof ResourceNotFoundException) {
                        return Mono.error(e); // Re-throw specific exception
                    }
                    return Mono.error(new InvalidSellerDataException(ApiResponseMessages.ERROR_UPDATING_SELLER + ": " + e.getMessage()));
                });
    }

    
    public Mono<Void> deleteSeller(Long id) {
        return sellerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .flatMap(sellerRepository::delete);
    }

    
    public Mono<Seller> getSellerById(Long id) {
        return sellerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)));
    }

    
    public Flux<Seller> getAllSellers(Long offset, Integer limit) {
        // Implement pagination at the repository level if possible (e.g., using R2DBC Pageable)
        // Otherwise, use skip/take for in-memory pagination (less efficient for large datasets)
        return sellerRepository.findAll().skip(offset).take(limit);
    }

    
    public Mono<Long> countAllSellers() {
        return sellerRepository.count();
    }

    
    public Flux<Seller> searchSellers(String query, Long offset, Integer limit) {
        // Assuming a repository method like findByNameContainingIgnoreCase(String name) or findByEmailContainingIgnoreCase
        return sellerRepository.findByNameContainingIgnoreCase(query, offset, limit);
    }

    
    public Mono<Long> countSearchSellers(String query) {
        return sellerRepository.countByNameContainingIgnoreCase(query);
    }
}