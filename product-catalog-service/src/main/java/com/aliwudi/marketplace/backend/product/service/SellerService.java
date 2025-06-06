package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.SellerRequest;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.InvalidSellerDataException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // Corrected package
import com.aliwudi.marketplace.backend.common.model.Seller;
import com.aliwudi.marketplace.backend.common.model.Store; // Import Store model for prepareDto
import com.aliwudi.marketplace.backend.product.repository.SellerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added for logging
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List; // For prepareDto List.of

import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@Service
@RequiredArgsConstructor
@Slf4j // Enables Lombok's logging
public class SellerService {

    private final SellerRepository sellerRepository;
    private final StoreService storeService; // Injected to fetch store details for prepareDto

    // IMPORTANT: This prepareDto method is moved from the controller
    // and kept *exactly* as provided by you. It is now a private helper method
    // within the service to enrich the entities before they are returned.
    /**
     * Helper method to map Seller entity to Seller DTO for public exposure.
     * This method enriches the Seller object with its associated Store details
     * by making integration calls.
     */
    private Mono<Seller> prepareDto(Seller seller) {
        if (seller == null) {
            return Mono.empty();
        }
        List<Mono<?>> listMonos = new java.util.ArrayList<>();

        // Fetch stores for the seller if not already set
        if (seller.getStores() == null && seller.getId() != null) {
            // Use getStoresBySeller without pagination to get all stores for the seller
            Flux<Store> storesFlux = storeService.getStoresBySeller(seller.getId());
            Mono<List<Store>> storeListMono = storesFlux.collectList()
                .doOnNext(seller::setStores) // Set stores on the seller
                .onErrorResume(e -> {
                    log.warn("Failed to fetch stores for seller {}: {}", seller.getId(), e.getMessage());
                    seller.setStores(List.of()); // Set empty list if fetching fails
                    return Mono.empty(); // Continue with other enrichments
                });
            listMonos.add(storeListMono);
        }

        if (listMonos.isEmpty()) {
            return Mono.just(seller);
        }

        // Use Mono.zip to wait for all enrichment Monos to complete.
        // The doOnNext in the above block will have already populated the seller object.
        return Mono.zip(listMonos, (Object[] array) -> seller)
                   .defaultIfEmpty(seller); // Ensure seller is returned even if zip is empty or throws error in one path
    }

    /**
     * Creates a new seller.
     * Checks for duplicate email before saving.
     *
     * @param sellerRequest The DTO containing seller creation data.
     * @return A Mono emitting the created Seller (enriched).
     * @throws DuplicateResourceException if a seller with the same email already exists.
     * @throws InvalidSellerDataException if provided data is invalid.
     */
    public Mono<Seller> createSeller(SellerRequest sellerRequest) {
        log.info("Attempting to create seller with email: {}", sellerRequest.getEmail());
        // Check for duplicate email
        return sellerRepository.existsByEmail(sellerRequest.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Duplicate seller email detected: {}", sellerRequest.getEmail());
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_SELLER_EMAIL));
                    }
                    // Validate basic seller data, e.g., name and email not blank
                    if (sellerRequest.getName() == null || sellerRequest.getName().isBlank() ||
                        sellerRequest.getEmail() == null || sellerRequest.getEmail().isBlank()) {
                        log.warn("Invalid seller data provided for creation: {}", sellerRequest);
                        return Mono.error(new InvalidSellerDataException(ApiResponseMessages.INVALID_SELLER_CREATION_REQUEST));
                    }

                    Seller seller = Seller.builder()
                            .name(sellerRequest.getName())
                            .email(sellerRequest.getEmail())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return sellerRepository.save(seller);
                })
                .flatMap(this::prepareDto) // Enrich the created seller
                .doOnSuccess(seller -> log.info("Seller created successfully with ID: {}", seller.getId()))
                .doOnError(e -> log.error("Error creating seller {}: {}", sellerRequest.getEmail(), e.getMessage(), e));
    }

    /**
     * Updates an existing seller.
     * Finds the seller by ID, updates its fields based on the request, and saves it.
     *
     * @param id The ID of the seller to update.
     * @param sellerRequest The DTO containing seller update data.
     * @return A Mono emitting the updated Seller (enriched).
     * @throws ResourceNotFoundException if the seller is not found.
     * @throws InvalidSellerDataException if provided data is invalid.
     */
    public Mono<Seller> updateSeller(Long id, SellerRequest sellerRequest) {
        log.info("Attempting to update seller with ID: {}", id);
        return sellerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .flatMap(existingSeller -> {
                    if (sellerRequest.getName() != null && !sellerRequest.getName().isBlank()) {
                        existingSeller.setName(sellerRequest.getName());
                    }
                    // Email usually not updatable directly or requires specific logic (e.g., re-verification)
                    // If email update is allowed, you'd need to check for duplicate emails here as well,
                    // excluding the current seller's email.
                    // Example:
                    // Mono<Void> emailCheck = Mono.empty();
                    // if (sellerRequest.getEmail() != null && !sellerRequest.getEmail().isBlank() && !existingSeller.getEmail().equalsIgnoreCase(sellerRequest.getEmail())) {
                    //     emailCheck = sellerRepository.existsByEmail(sellerRequest.getEmail())
                    //         .flatMap(isDuplicate -> {
                    //             if (isDuplicate) {
                    //                 return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_SELLER_EMAIL));
                    //             }
                    //             existingSeller.setEmail(sellerRequest.getEmail());
                    //             return Mono.empty();
                    //         });
                    // }
                    // return emailCheck.thenReturn(existingSeller); // Continue chain with the potentially updated seller

                    existingSeller.setUpdatedAt(LocalDateTime.now());
                    return sellerRepository.save(existingSeller);
                })
                .flatMap(this::prepareDto) // Enrich the updated seller
                .doOnSuccess(seller -> log.info("Seller updated successfully with ID: {}", seller.getId()))
                .doOnError(e -> log.error("Error updating seller {}: {}", id, e.getMessage(), e));
    }

    /**
     * Deletes a seller by their ID.
     *
     * @param id The ID of the seller to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the seller is not found.
     */
    public Mono<Void> deleteSeller(Long id) {
        log.info("Attempting to delete seller with ID: {}", id);
        return sellerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .flatMap(sellerRepository::delete)
                .doOnSuccess(v -> log.info("Seller deleted successfully with ID: {}", id))
                .doOnError(e -> log.error("Error deleting seller {}: {}", id, e.getMessage(), e));
    }

    /**
     * Retrieves a seller by their ID, enriching it.
     *
     * @param id The ID of the seller to retrieve.
     * @return A Mono emitting the Seller if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the seller is not found.
     */
    public Mono<Seller> getSellerById(Long id) {
        log.info("Retrieving seller by ID: {}", id);
        return sellerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .flatMap(this::prepareDto) // Enrich the seller
                .doOnSuccess(seller -> log.info("Seller retrieved successfully: {}", seller.getId()))
                .doOnError(e -> log.error("Error retrieving seller {}: {}", id, e.getMessage(), e));
    }

    /**
     * Retrieves a seller by their unique email address, enriching it.
     *
     * @param email The email address of the seller.
     * @return A Mono emitting the Seller if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the seller is not found by email.
     */
    public Mono<Seller> getSellerByEmail(String email) {
        log.info("Retrieving seller by email: {}", email);
        return sellerRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND_BY_EMAIL + email)))
                .flatMap(this::prepareDto) // Enrich the seller
                .doOnSuccess(seller -> log.info("Seller retrieved successfully by email: {}", seller.getEmail()))
                .doOnError(e -> log.error("Error retrieving seller by email {}: {}", email, e.getMessage(), e));
    }

    /**
     * Retrieves all sellers with pagination, enriching each.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting all sellers (enriched).
     */
    public Flux<Seller> getAllSellers(int page, int size) {
        log.info("Retrieving all sellers with page {} and size {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending()); // Default sort for consistency
        return sellerRepository.findAllBy(pageable)
                .flatMap(this::prepareDto) // Enrich each seller
                .doOnComplete(() -> log.info("Finished retrieving all sellers for page {} with size {}.", page, size))
                .doOnError(e -> log.error("Error retrieving all sellers: {}", e.getMessage(), e));
    }

    /**
     * Counts all sellers.
     *
     * @return A Mono emitting the total count of sellers.
     */
    public Mono<Long> countAllSellers() {
        log.info("Counting all sellers.");
        return sellerRepository.count()
                .doOnSuccess(count -> log.info("Total seller count: {}", count))
                .doOnError(e -> log.error("Error counting all sellers: {}", e.getMessage(), e));
    }

    /**
     * Searches sellers by name (case-insensitive, contains) with pagination, enriching each.
     *
     * @param query The search query for seller name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching sellers (enriched).
     */
    public Flux<Seller> searchSellers(String query, int page, int size) {
        log.info("Searching sellers for query '{}' with page {} and size {}", query, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return sellerRepository.findByNameContainingIgnoreCase(query, pageable)
                .flatMap(this::prepareDto) // Enrich each seller
                .doOnComplete(() -> log.info("Finished searching sellers for query '{}' for page {} with size {}.", query, page, size))
                .doOnError(e -> log.error("Error searching sellers for query {}: {}", query, e.getMessage(), e));
    }

    /**
     * Counts sellers by name (case-insensitive, contains).
     *
     * @param query The search query for seller name.
     * @return A Mono emitting the count of matching sellers.
     */
    public Mono<Long> countSearchSellers(String query) {
        log.info("Counting search results for seller name query '{}'", query);
        return sellerRepository.countByNameContainingIgnoreCase(query)
                .doOnSuccess(count -> log.info("Total search result count for name query '{}': {}", query, count))
                .doOnError(e -> log.error("Error counting search results for seller name query {}: {}", query, e.getMessage(), e));
    }

    /**
     * Checks if a seller with a given email already exists.
     *
     * @param email The email to check for existence.
     * @return A Mono emitting true if a seller with the email exists, false otherwise.
     */
    public Mono<Boolean> existsByEmail(String email) {
        log.info("Checking if seller exists by email: {}", email);
        return sellerRepository.existsByEmail(email)
                .doOnSuccess(exists -> log.info("Seller with email {} exists: {}", email, exists))
                .doOnError(e -> log.error("Error checking existence of seller by email {}: {}", email, e.getMessage(), e));
    }
}
