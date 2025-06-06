package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.StoreRequest;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.InvalidStoreDataException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // Corrected package
import com.aliwudi.marketplace.backend.common.model.Store;
import com.aliwudi.marketplace.backend.common.model.Seller; // Import Seller model for prepareDto
import com.aliwudi.marketplace.backend.common.model.Product; // Import Product model for prepareDto
import com.aliwudi.marketplace.backend.product.repository.StoreRepository;

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
import com.aliwudi.marketplace.backend.product.repository.SellerRepository; // Already imported for sellerExistsMono

@Service
@RequiredArgsConstructor
@Slf4j // Enables Lombok's logging
public class StoreService {

    private final StoreRepository storeRepository;
    private final SellerRepository sellerRepository;
    private final ProductService productService; // Injected to fetch product details for prepareDto

    // IMPORTANT: This prepareDto method is moved from the controller
    // and kept *exactly* as provided by you. It is now a private helper method
    // within the service to enrich the entities before they are returned.
    /**
     * Helper method to map Store entity to Store DTO for public exposure.
     * This method enriches the Store object with Seller and Product details.
     */
    private Mono<Store> prepareDto(Store store) {
        if (store == null) {
            return Mono.empty();
        }

        // List to hold monos for concurrent enrichment
        List<Mono<?>> enrichmentMonos = new java.util.ArrayList<>();

        // Fetch Seller if not already set
        if (store.getSeller() == null && store.getSellerId() != null) {
            Mono<Seller> sellerMono = sellerRepository.findById(store.getSellerId())
                .doOnNext(store::setSeller)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch seller {} for store {}: {}", store.getSellerId(), store.getId(), e.getMessage());
                    store.setSeller(null); // Set to null if fetching fails
                    return Mono.empty(); // Continue with other enrichments
                });
            enrichmentMonos.add(sellerMono);
        }

        // Fetch Products if not already set (using ProductService)
        if (store.getProducts() == null && store.getId() != null) {
            // Using default pagination for products in prepareDto as per original controller
            Pageable pageable = PageRequest.of(0, 20);
            Mono<List<Product>> productsListMono = productService.getProductsByStore(store.getId(), pageable.getPageNumber(), pageable.getPageSize())
                .collectList()
                .doOnNext(store::setProducts)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch products for store {}: {}", store.getId(), e.getMessage());
                    store.setProducts(List.of()); // Set empty list if fetching fails
                    return Mono.empty(); // Continue with other enrichments
                });
            enrichmentMonos.add(productsListMono);
        }

        if (enrichmentMonos.isEmpty()) {
            return Mono.just(store);
        }

        // Use Mono.zip to wait for all enrichment Monos to complete.
        // The doOnNext calls above will have already populated the store object.
        return Mono.zip(enrichmentMonos, (Object[] array) -> store)
                   .defaultIfEmpty(store); // Ensure store is returned even if zip is empty or throws error in one path
    }

    /**
     * Creates a new store.
     * Validates seller existence and checks for duplicate store name for that seller.
     *
     * @param storeRequest The DTO containing store creation data.
     * @return A Mono emitting the created Store (enriched).
     * @throws ResourceNotFoundException if the seller is not found.
     * @throws DuplicateResourceException if a store with the same name already exists for the seller.
     * @throws InvalidStoreDataException if provided data is invalid.
     */
    public Mono<Store> createStore(StoreRequest storeRequest) {
        log.info("Attempting to create store: {} for seller {}", storeRequest.getName(), storeRequest.getSellerId());

        // Validate seller existence
        Mono<Void> sellerExistsCheck = sellerRepository.existsById(storeRequest.getSellerId())
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + storeRequest.getSellerId())))
                .then(); // Convert to Mono<Void> as we only care about existence

        // Check for duplicate store name for this specific seller
        Mono<Void> duplicateNameCheck = storeRepository.existsByNameIgnoreCaseAndSellerId(storeRequest.getName(), storeRequest.getSellerId())
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Duplicate store name '{}' for seller ID {}.", storeRequest.getName(), storeRequest.getSellerId());
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_STORE_NAME_FOR_SELLER));
                    }
                    return Mono.empty(); // Name is unique for this seller, continue
                });

        return Mono.when(sellerExistsCheck, duplicateNameCheck) // Ensure both checks pass
                .thenReturn(Store.builder()
                        .name(storeRequest.getName())
                        .description(storeRequest.getDescription())
                        .address(storeRequest.getAddress())
                        .sellerId(storeRequest.getSellerId())
                        .locationId(storeRequest.getLocationId())
                        .rating(0.0) // Initialize rating
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
                .flatMap(storeRepository::save)
                .flatMap(this::prepareDto) // Enrich the created store
                .doOnSuccess(store -> log.info("Store created successfully with ID: {}", store.getId()))
                .doOnError(e -> log.error("Error creating store {}: {}", storeRequest.getName(), e.getMessage(), e));
    }

    /**
     * Updates an existing store.
     * Finds the store by ID, updates its fields based on the request, and saves it.
     *
     * @param id The ID of the store to update.
     * @param storeRequest The DTO containing store update data.
     * @return A Mono emitting the updated Store (enriched).
     * @throws ResourceNotFoundException if the store is not found.
     * @throws DuplicateResourceException if the updated name causes a duplicate for the same seller.
     * @throws InvalidStoreDataException if provided data is invalid.
     */
    public Mono<Store> updateStore(Long id, StoreRequest storeRequest) {
        log.info("Attempting to update store with ID: {}", id);
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .flatMap(existingStore -> {
                    // Check for duplicate name if name is being updated and it's different from current name
                    Mono<Void> nameCheck = Mono.empty();
                    if (storeRequest.getName() != null && !storeRequest.getName().isBlank() && !existingStore.getName().equalsIgnoreCase(storeRequest.getName())) {
                        nameCheck = storeRepository.existsByNameIgnoreCaseAndSellerId(storeRequest.getName(), existingStore.getSellerId())
                                .flatMap(isDuplicate -> {
                                    if (isDuplicate) {
                                        log.warn("Attempt to update store name to a duplicate: {}", storeRequest.getName());
                                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.DUPLICATE_STORE_NAME_FOR_SELLER));
                                    }
                                    return Mono.empty();
                                });
                    }
                    // Wait for name check to complete before proceeding with updates
                    return nameCheck.thenReturn(existingStore);
                })
                .flatMap(existingStore -> {
                    if (storeRequest.getName() != null && !storeRequest.getName().isBlank()) {
                        existingStore.setName(storeRequest.getName());
                    }
                    if (storeRequest.getDescription() != null && !storeRequest.getDescription().isBlank()) {
                        existingStore.setDescription(storeRequest.getDescription());
                    }
                    if (storeRequest.getAddress() != null && !storeRequest.getAddress().isBlank()) {
                        existingStore.setAddress(storeRequest.getAddress());
                    }
                    if (storeRequest.getLocationId() != null) {
                        existingStore.setLocationId(storeRequest.getLocationId());
                    }
                    // Rating is typically updated via review aggregation, not directly via this endpoint
                    existingStore.setUpdatedAt(LocalDateTime.now());
                    return storeRepository.save(existingStore);
                })
                .flatMap(this::prepareDto) // Enrich the updated store
                .doOnSuccess(store -> log.info("Store updated successfully with ID: {}", store.getId()))
                .doOnError(e -> log.error("Error updating store {}: {}", id, e.getMessage(), e));
    }

    /**
     * Deletes a store by its ID.
     *
     * @param id The ID of the store to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the store is not found.
     */
    public Mono<Void> deleteStore(Long id) {
        log.info("Attempting to delete store with ID: {}", id);
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .flatMap(storeRepository::delete)
                .doOnSuccess(v -> log.info("Store deleted successfully with ID: {}", id))
                .doOnError(e -> log.error("Error deleting store {}: {}", id, e.getMessage(), e));
    }

    /**
     * Retrieves a store by its ID, enriching it.
     *
     * @param id The ID of the store to retrieve.
     * @return A Mono emitting the Store if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the store is not found.
     */
    public Mono<Store> getStoreById(Long id) {
        log.info("Retrieving store by ID: {}", id);
        return storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + id)))
                .flatMap(this::prepareDto) // Enrich the store
                .doOnSuccess(store -> log.info("Store retrieved successfully: {}", store.getId()))
                .doOnError(e -> log.error("Error retrieving store {}: {}", id, e.getMessage(), e));
    }

    /**
     * Retrieves all stores with pagination, enriching each.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting all stores (enriched).
     */
    public Flux<Store> getAllStores(int page, int size) {
        log.info("Retrieving all stores with page {} and size {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        return storeRepository.findAllBy(pageable)
                .flatMap(this::prepareDto) // Enrich each store
                .doOnComplete(() -> log.info("Finished retrieving all stores for page {} with size {}.", page, size))
                .doOnError(e -> log.error("Error retrieving all stores: {}", e.getMessage(), e));
    }

    /**
     * Counts all stores.
     *
     * @return A Mono emitting the total count of stores.
     */
    public Mono<Long> countAllStores() {
        log.info("Counting all stores.");
        return storeRepository.count()
                .doOnSuccess(count -> log.info("Total store count: {}", count))
                .doOnError(e -> log.error("Error counting all stores: {}", e.getMessage(), e));
    }

    /**
     * Finds all stores owned by a specific seller with pagination, enriching each.
     *
     * @param sellerId The ID of the seller.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting stores owned by the specified seller (enriched).
     * @throws ResourceNotFoundException if the seller is not found.
     */
    public Flux<Store> getStoresBySeller(Long sellerId, int page, int size) {
        log.info("Retrieving stores for seller ID {} with page {} and size {}", sellerId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return sellerRepository.existsById(sellerId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + sellerId)))
                .flatMapMany(exists -> storeRepository.findBySellerId(sellerId, pageable))
                .flatMap(this::prepareDto) // Enrich each store
                .doOnComplete(() -> log.info("Finished retrieving stores for seller ID {} for page {} with size {}.", sellerId, page, size))
                .doOnError(e -> log.error("Error retrieving stores for seller {}: {}", sellerId, e.getMessage(), e));
    }

    /**
     * Finds all stores owned by a specific seller without pagination, enriching each.
     * This method is primarily used internally for `prepareDto` of Seller.
     *
     * @param sellerId The ID of the seller.
     * @return A Flux emitting stores owned by the specified seller (enriched).
     * @throws ResourceNotFoundException if the seller is not found.
     */
    public Flux<Store> getStoresBySeller(Long sellerId) {
        log.info("Retrieving all stores for seller ID {}", sellerId);
        return sellerRepository.existsById(sellerId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + sellerId)))
                .flatMapMany(exists -> storeRepository.findBySellerId(sellerId))
                .flatMap(this::prepareDto) // Enrich each store
                .doOnComplete(() -> log.info("Finished retrieving all stores for seller ID {}.", sellerId))
                .doOnError(e -> log.error("Error retrieving all stores for seller {}: {}", sellerId, e.getMessage(), e));
    }

    /**
     * Counts all stores owned by a specific seller.
     *
     * @param sellerId The ID of the seller.
     * @return A Mono emitting the total count of stores for the seller.
     * @throws ResourceNotFoundException if the seller is not found.
     */
    public Mono<Long> countStoresBySeller(Long sellerId) {
        log.info("Counting stores for seller ID {}", sellerId);
        return sellerRepository.existsById(sellerId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + sellerId)))
                .flatMap(exists -> storeRepository.countBySellerId(sellerId))
                .doOnSuccess(count -> log.info("Total store count for seller {}: {}", sellerId, count))
                .doOnError(e -> log.error("Error counting stores for seller {}: {}", sellerId, e.getMessage(), e));
    }

    /**
     * Searches stores by name (case-insensitive, contains) with pagination, enriching each.
     *
     * @param name The search query for store name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching stores (enriched).
     */
    public Flux<Store> searchStoresByName(String name, int page, int size) {
        log.info("Searching stores for name '{}' with page {} and size {}", name, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return storeRepository.findByNameContainingIgnoreCase(name, pageable)
                .flatMap(this::prepareDto) // Enrich each store
                .doOnComplete(() -> log.info("Finished searching stores for name '{}' for page {} with size {}.", name, page, size))
                .doOnError(e -> log.error("Error searching stores for name {}: {}", name, e.getMessage(), e));
    }

    /**
     * Counts stores by name (case-insensitive, contains).
     *
     * @param name The search query for store name.
     * @return A Mono emitting the count of matching stores.
     */
    public Mono<Long> countSearchStoresByName(String name) {
        log.info("Counting search results for store name query '{}'", name);
        return storeRepository.countByNameContainingIgnoreCase(name)
                .doOnSuccess(count -> log.info("Total search result count for name query '{}': {}", name, count))
                .doOnError(e -> log.error("Error counting search results for store name query {}: {}", name, e.getMessage(), e));
    }

    /**
     * Searches stores by locationId (case-insensitive, contains) with pagination, enriching each.
     *
     * @param locationId The search query for location ID.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching stores (enriched).
     */
    public Flux<Store> searchStoresByLocationId(String locationId, int page, int size) {
        log.info("Searching stores for location ID '{}' with page {} and size {}", locationId, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending()); // Assuming you want to sort by name
        return storeRepository.findByLocationIdContainingIgnoreCase(locationId, pageable)
                .flatMap(this::prepareDto) // Enrich each store
                .doOnComplete(() -> log.info("Finished searching stores for location ID '{}' for page {} with size {}.", locationId, page, size))
                .doOnError(e -> log.error("Error searching stores for location ID {}: {}", locationId, e.getMessage(), e));
    }

    /**
     * Counts stores by locationId (case-insensitive, contains).
     *
     * @param locationId The search query for location ID.
     * @return A Mono emitting the count of matching stores.
     */
    public Mono<Long> countSearchStoresByLocationId(String locationId) {
        log.info("Counting search results for location ID query '{}'", locationId);
        return storeRepository.countByLocationIdContainingIgnoreCase(locationId)
                .doOnSuccess(count -> log.info("Total search result count for location ID query '{}': {}", locationId, count))
                .doOnError(e -> log.error("Error counting search results for location ID query {}: {}", locationId, e.getMessage(), e));
    }

    /**
     * Finds stores with a rating greater than or equal to a minimum value, with pagination, enriching each.
     *
     * @param minRating The minimum rating (inclusive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting filtered stores (enriched).
     * @throws InvalidStoreDataException if minRating is out of range.
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    public Flux<Store> getStoresByMinRating(Double minRating, int page, int size) {
        log.info("Retrieving stores with min rating {} with page {} and size {}", minRating, page, size);
        if (minRating == null || minRating < 0.0 || minRating > 5.0) {
            log.warn("Invalid minRating provided: {}", minRating);
            return Flux.error(new InvalidStoreDataException(ApiResponseMessages.INVALID_RATING_RANGE));
        }

        // Pagination validation is now handled in controller, but keeping here for robustness
        if (page < 0) {
            return Flux.error(new IllegalArgumentException("Page number cannot be negative"));
        }
        if (size <= 0) {
            return Flux.error(new IllegalArgumentException("Page size must be greater than zero"));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("rating").descending());
        return storeRepository.findByRatingGreaterThanEqual(minRating, pageable)
                .flatMap(this::prepareDto) // Enrich each store
                .doOnComplete(() -> log.info("Finished retrieving stores with min rating {} for page {} with size {}.", minRating, page, size))
                .doOnError(e -> log.error("Error retrieving stores with min rating {}: {}", minRating, e.getMessage(), e));
    }

    /**
     * Counts stores with a rating greater than or equal to a minimum value.
     *
     * @param minRating The minimum rating (inclusive).
     * @return A Mono emitting the count of filtered stores.
     * @throws InvalidStoreDataException if minRating is out of range.
     */
    public Mono<Long> countStoresByMinRating(Double minRating) {
        log.info("Counting stores with min rating {}", minRating);
        if (minRating == null || minRating < 0.0 || minRating > 5.0) {
            log.warn("Invalid minRating provided for count: {}", minRating);
            return Mono.error(new InvalidStoreDataException(ApiResponseMessages.INVALID_RATING_RANGE));
        }
        return storeRepository.countByRatingGreaterThanEqual(minRating)
                .doOnSuccess(count -> log.info("Total store count with min rating {}: {}", minRating, count))
                .doOnError(e -> log.error("Error counting stores with min rating {}: {}", minRating, e.getMessage(), e));
    }

    /**
     * Checks if a store with a given name exists for a specific seller.
     *
     * @param name The name of the store.
     * @param sellerId The ID of the seller.
     * @return A Mono emitting true if a store with the name exists for the seller, false otherwise.
     */
    public Mono<Boolean> existsStoreByNameAndSeller(String name, Long sellerId) {
        log.info("Checking if store '{}' exists for seller {}", name, sellerId);
        return storeRepository.existsByNameIgnoreCaseAndSellerId(name, sellerId)
                .doOnSuccess(exists -> log.info("Store '{}' for seller {} exists: {}", name, sellerId, exists))
                .doOnError(e -> log.error("Error checking store existence for name '{}' and seller {}: {}", name, sellerId, e.getMessage(), e));
    }
}
