package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.SellerRequest;
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.product.exception.InvalidSellerDataException;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.model.Seller;
import com.aliwudi.marketplace.backend.product.repository.SellerRepository;

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
public class SellerService{

    private final SellerRepository sellerRepository;

    /**
     * Creates a new seller.
     * Checks for duplicate email before saving.
     *
     * @param sellerRequest The DTO containing seller creation data.
     * @return A Mono emitting the created Seller.
     */
    public Mono<Seller> createSeller(SellerRequest sellerRequest) {
        // Check for duplicate email
        return sellerRepository.existsByEmail(sellerRequest.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_SELLER_EMAIL));
                    }
                    Seller seller = Seller.builder()
                            .name(sellerRequest.getName())
                            .email(sellerRequest.getEmail())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return sellerRepository.save(seller);
                });
    }

    /**
     * Updates an existing seller.
     * Finds the seller by ID, updates its fields based on the request, and saves it.
     *
     * @param id The ID of the seller to update.
     * @param sellerRequest The DTO containing seller update data.
     * @return A Mono emitting the updated Seller.
     */
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
                });
    }

    /**
     * Deletes a seller by their ID.
     *
     * @param id The ID of the seller to delete.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteSeller(Long id) {
        return sellerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .flatMap(sellerRepository::delete);
    }

    /**
     * Retrieves a seller by their ID.
     *
     * @param id The ID of the seller to retrieve.
     * @return A Mono emitting the Seller if found, or an error if not.
     */
    public Mono<Seller> getSellerById(Long id) {
        return sellerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)));
    }

    /**
     * Retrieves a seller by their unique email address.
     *
     * @param email The email address of the seller.
     * @return A Mono emitting the Seller if found, or an error if not.
     */
    public Mono<Seller> getSellerByEmail(String email) {
        return sellerRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND_BY_EMAIL + email)));
    }

    /**
     * Retrieves all sellers with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting all sellers.
     */
    public Flux<Seller> getAllSellers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size); // Default sort by ID or creation date if needed
        return sellerRepository.findAllBy(pageable);
    }

    /**
     * Counts all sellers.
     *
     * @return A Mono emitting the total count of sellers.
     */
    public Mono<Long> countAllSellers() {
        return sellerRepository.count();
    }

    /**
     * Searches sellers by name (case-insensitive, contains) with pagination.
     *
     * @param query The search query for seller name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching sellers.
     */
    public Flux<Seller> searchSellers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending()); // Sort by name for search results
        return sellerRepository.findByNameContainingIgnoreCase(query, pageable);
    }

    /**
     * Counts sellers by name (case-insensitive, contains).
     *
     * @param query The search query for seller name.
     * @return A Mono emitting the count of matching sellers.
     */
    public Mono<Long> countSearchSellers(String query) {
        return sellerRepository.countByNameContainingIgnoreCase(query);
    }

    /**
     * Checks if a seller with a given email already exists.
     *
     * @param email The email to check for existence.
     * @return A Mono emitting true if a seller with the email exists, false otherwise.
     */
    public Mono<Boolean> existsByEmail(String email) {
        return sellerRepository.existsByEmail(email);
    }
}