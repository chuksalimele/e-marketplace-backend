package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.common.model.Product;
import com.aliwudi.marketplace.backend.common.model.Store; // Import Store model for prepareDto
import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // Corrected package for ResourceNotFoundException
import com.aliwudi.marketplace.backend.common.exception.InvalidProductDataException; // Corrected package for InvalidProductDataException
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // Corrected package for DuplicateResourceException

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Added for logging
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Added for sort
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.product.service.StoreService; // To fetch store details for prepareDto

import java.util.List; // For prepareDto List.of

@Service
@RequiredArgsConstructor
@Slf4j // Enables Lombok's logging
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreService storeService; // Injected to fetch store details for prepareDto

    // IMPORTANT: This prepareDto method is moved from the controller
    // and kept *exactly* as provided by you. It is now a private helper method
    // within the service to enrich the entities before they are returned.
    /**
     * Helper method to map Product entity to Product DTO for public exposure.
     * This method enriches the Product object with Store details
     * by making integration calls.
     */
    private Mono<Product> prepareDto(Product product) {
        if (product == null) {
            return Mono.empty();
        }
        Mono<Store> storeMono;
        List<Mono<?>> listMonos = new java.util.ArrayList<>(); // Use ArrayList for mutable list

        if (product.getStore() == null && product.getStoreId() != null) {
            storeMono = storeService.getStoreById(product.getStoreId());
            listMonos.add(storeMono);
        }

        if (listMonos.isEmpty()) {
            return Mono.just(product);
        }

        return Mono.zip(listMonos, (Object[] array) -> {
            for (Object obj : array) {
                if (obj instanceof Store store) {
                    product.setStore(store);
                }
            }
            return product;
        });
    }

    /**
     * Creates a new product.
     * Performs basic validation for price and stock quantity.
     * Checks for duplicate product name for the seller.
     *
     * @param productRequest The DTO containing product creation data.
     * @return A Mono emitting the created Product (enriched).
     * @throws InvalidProductDataException if price or stock quantity are negative or product name already exists for seller.
     * @throws ResourceNotFoundException if the associated store or seller is not found.
     */
    public Mono<Product> createProduct(ProductRequest productRequest) {
        log.info("Attempting to create product: {}", productRequest.getName());

        if (productRequest.getPrice().doubleValue() < 0 || productRequest.getStockQuantity() < 0) {
            log.warn("Attempt to create product with negative price or stock: {}", productRequest.getName());
            return Mono.error(new InvalidProductDataException("Price and stock quantity cannot be negative."));
        }

        // Validate that the store exists and the seller for that store exists
        Mono<Store> storeExistsCheck = storeService.getStoreById(productRequest.getStoreId())
            .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + productRequest.getStoreId())))
            .filter(store -> store.getSellerId().equals(productRequest.getSellerId())) // Ensure seller owns the store
            .switchIfEmpty(Mono.error(new InvalidProductDataException("Seller " + productRequest.getSellerId() + " does not own store " + productRequest.getStoreId())));

        // Check if a product with the same name already exists for the seller in that store
        Mono<Boolean> duplicateNameCheck = productRepository.existsByNameIgnoreCaseAndSellerId(productRequest.getName(), productRequest.getSellerId())
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Duplicate product name '{}' for seller ID {}", productRequest.getName(), productRequest.getSellerId());
                        return Mono.error(new DuplicateResourceException("Product with name '" + productRequest.getName() + "' already exists for this seller."));
                    }
                    return Mono.just(false); // Name is unique for this seller
                });

        return Mono.when(storeExistsCheck, duplicateNameCheck) // Ensure both checks pass
                .thenReturn(Product.builder()
                        .name(productRequest.getName())
                        .description(productRequest.getDescription())
                        .price(productRequest.getPrice())
                        .stockQuantity(productRequest.getStockQuantity())
                        .category(productRequest.getCategory())
                        .storeId(productRequest.getStoreId())
                        .sellerId(productRequest.getSellerId())
                        .imageUrl(productRequest.getImageUrl())
                        .locationId(productRequest.getLocationId())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
                .flatMap(productRepository::save)
                .flatMap(this::prepareDto) // Enrich the created product
                .doOnSuccess(product -> log.info("Product created successfully with ID: {}", product.getId()))
                .doOnError(e -> log.error("Error creating product: {}", e.getMessage(), e));
    }

    /**
     * Updates an existing product.
     * Finds the product by ID, updates its fields based on the request, and saves it.
     *
     * @param id The ID of the product to update.
     * @param productRequest The DTO containing product update data.
     * @return A Mono emitting the updated Product (enriched).
     * @throws ResourceNotFoundException if the product is not found.
     * @throws InvalidProductDataException if price or stock quantity are negative, or if a duplicate name is introduced.
     */
    public Mono<Product> updateProduct(Long id, ProductRequest productRequest) {
        log.info("Attempting to update product with ID: {}", id);
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .flatMap(existingProduct -> {
                    // Check for duplicate name if name is being updated
                    Mono<Void> nameCheck = Mono.empty();
                    if (productRequest.getName() != null && !productRequest.getName().isBlank() && !existingProduct.getName().equalsIgnoreCase(productRequest.getName())) {
                        nameCheck = productRepository.existsByNameIgnoreCaseAndSellerId(productRequest.getName(), existingProduct.getSellerId())
                                .flatMap(isDuplicate -> {
                                    if (isDuplicate) {
                                        log.warn("Attempt to update product name to a duplicate: {}", productRequest.getName());
                                        return Mono.error(new DuplicateResourceException("Product with name '" + productRequest.getName() + "' already exists for this seller."));
                                    }
                                    return Mono.empty();
                                });
                    }

                    // Perform updates after name check (if any)
                    return nameCheck.thenReturn(existingProduct);
                })
                .flatMap(existingProduct -> {
                    if (productRequest.getName() != null && !productRequest.getName().isBlank()) {
                        existingProduct.setName(productRequest.getName());
                    }
                    if (productRequest.getDescription() != null && !productRequest.getDescription().isBlank()) {
                        existingProduct.setDescription(productRequest.getDescription());
                    }
                    if (productRequest.getPrice() != null) {
                        if (productRequest.getPrice().doubleValue() >= 0) {
                            existingProduct.setPrice(productRequest.getPrice());
                        } else {
                            log.warn("Attempt to update product {} with negative price: {}", id, productRequest.getPrice());
                            return Mono.error(new InvalidProductDataException("Price cannot be negative."));
                        }
                    }
                    if (productRequest.getStockQuantity() != null) {
                        if (productRequest.getStockQuantity() >= 0) {
                            existingProduct.setStockQuantity(productRequest.getStockQuantity());
                        } else {
                            log.warn("Attempt to update product {} with negative stock quantity: {}", id, productRequest.getStockQuantity());
                            return Mono.error(new InvalidProductDataException("Stock quantity cannot be negative."));
                        }
                    }
                    if (productRequest.getCategory() != null && !productRequest.getCategory().isBlank()) {
                        existingProduct.setCategory(productRequest.getCategory());
                    }
                    // Only update storeId and sellerId if they are provided and valid (e.g., if changing seller, perform checks)
                    if (productRequest.getStoreId() != null && !productRequest.getStoreId().equals(existingProduct.getStoreId())) {
                        // In a real scenario, you'd likely validate the new storeId and its association with sellerId
                        existingProduct.setStoreId(productRequest.getStoreId());
                    }
                    if (productRequest.getSellerId() != null && !productRequest.getSellerId().equals(existingProduct.getSellerId())) {
                         existingProduct.setSellerId(productRequest.getSellerId());
                    }
                    if (productRequest.getImageUrl() != null && !productRequest.getImageUrl().isBlank()) {
                        existingProduct.setImageUrl(productRequest.getImageUrl());
                    }
                    if (productRequest.getLocationId() != null ) {
                        existingProduct.setLocationId(productRequest.getLocationId());
                    }

                    existingProduct.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(existingProduct);
                })
                .flatMap(this::prepareDto) // Enrich the updated product
                .doOnSuccess(product -> log.info("Product updated successfully with ID: {}", product.getId()))
                .doOnError(e -> log.error("Error updating product {}: {}", id, e.getMessage(), e));
    }

    /**
     * Decreases the stock quantity of a product.
     *
     * @param productId The ID of the product whose stock to decrease.
     * @param quantity The amount to decrease the stock by. Must be positive.
     * @return A Mono emitting the updated Product with decreased stock (enriched).
     * @throws ResourceNotFoundException if the product is not found.
     * @throws InvalidProductDataException if the quantity is invalid (negative or exceeds current stock).
     */
    public Mono<Product> decreaseAndSaveStock(Long productId, Integer quantity) {
        log.info("Decreasing stock for product ID {} by quantity {}", productId, quantity);
        if (quantity == null || quantity <= 0) {
            log.warn("Invalid quantity provided for stock decrease: {}", quantity);
            return Mono.error(new InvalidProductDataException(ApiResponseMessages.INVALID_STOCK_DECREMENT_QUANTITY));
        }

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .flatMap(existingProduct -> {
                    int currentStock = existingProduct.getStockQuantity();
                    if (currentStock < quantity) {
                        log.warn("Insufficient stock for product {}. Current: {}, Requested decrease: {}", productId, currentStock, quantity);
                        return Mono.error(new InvalidProductDataException(ApiResponseMessages.INSUFFICIENT_STOCK));
                    }
                    existingProduct.setStockQuantity(currentStock - quantity);
                    existingProduct.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(existingProduct);
                })
                .flatMap(this::prepareDto) // Enrich the updated product
                .doOnSuccess(product -> log.info("Stock for product {} decreased to {}", product.getId(), product.getStockQuantity()))
                .doOnError(e -> log.error("Error decreasing stock for product {}: {}", productId, e.getMessage(), e));
    }

    /**
     * Deletes a product by its ID.
     *
     * @param id The ID of the product to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the product is not found.
     */
    public Mono<Void> deleteProduct(Long id) {
        log.info("Attempting to delete product with ID: {}", id);
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .flatMap(productRepository::delete)
                .doOnSuccess(v -> log.info("Product deleted successfully with ID: {}", id))
                .doOnError(e -> log.error("Error deleting product {}: {}", id, e.getMessage(), e));
    }

    /**
     * Retrieves a product by its ID, enriching it.
     *
     * @param id The ID of the product to retrieve.
     * @return A Mono emitting the Product if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the product is not found.
     */
    public Mono<Product> getProductById(Long id) {
        log.info("Retrieving product by ID: {}", id);
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .flatMap(this::prepareDto) // Enrich the product
                .doOnSuccess(product -> log.info("Product retrieved successfully: {}", product.getId()))
                .doOnError(e -> log.error("Error retrieving product {}: {}", id, e.getMessage(), e));
    }

    // --- All Products ---

    /**
     * Retrieves all products with pagination, enriching each.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     */
    public Flux<Product> getAllProducts(int page, int size) {
        log.info("Retrieving all products with page {} and size {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findAllBy(pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving all products for page {} with size {}.", page, size))
                .doOnError(e -> log.error("Error retrieving all products: {}", e.getMessage(), e));
    }

    /**
     * Counts all products.
     *
     * @return A Mono emitting the total count of products.
     */
    public Mono<Long> countAllProducts() {
        log.info("Counting all products.");
        return productRepository.count()
                .doOnSuccess(count -> log.info("Total product count: {}", count))
                .doOnError(e -> log.error("Error counting all products: {}", e.getMessage(), e));
    }

    // --- Products by Store ---

    /**
     * Finds products belonging to a specific store with pagination, enriching each.
     *
     * @param storeId The ID of the store.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     * @throws ResourceNotFoundException if the store is not found.
     */
    public Flux<Product> getProductsByStore(Long storeId, int page, int size) {
        log.info("Retrieving products for store ID {} with page {} and size {}", storeId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return storeService.getStoreById(storeId) // Ensure store exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + storeId)))
                .flatMapMany(store -> productRepository.findByStoreId(storeId, pageable))
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for store ID {} for page {} with size {}.", storeId, page, size))
                .doOnError(e -> log.error("Error retrieving products for store {}: {}", storeId, e.getMessage(), e));
    }

    /**
     * Counts products belonging to a specific store.
     *
     * @param storeId The ID of the store.
     * @return A Mono emitting the count of products for the store.
     * @throws ResourceNotFoundException if the store is not found.
     */
    public Mono<Long> countProductsByStore(Long storeId) {
        log.info("Counting products for store ID {}", storeId);
        return storeService.getStoreById(storeId) // Ensure store exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + storeId)))
                .flatMap(store -> productRepository.countByStoreId(storeId))
                .doOnSuccess(count -> log.info("Total product count for store {}: {}", storeId, count))
                .doOnError(e -> log.error("Error counting products for store {}: {}", storeId, e.getMessage(), e));
    }

    // --- Products by Seller ---

    /**
     * Finds products sold by a specific seller with pagination, enriching each.
     *
     * @param sellerId The ID of the seller.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     */
    public Flux<Product> getProductsBySeller(Long sellerId, int page, int size) {
        log.info("Retrieving products for seller ID {} with page {} and size {}", sellerId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findBySellerId(sellerId, pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for seller ID {} for page {} with size {}.", sellerId, page, size))
                .doOnError(e -> log.error("Error retrieving products for seller {}: {}", sellerId, e.getMessage(), e));
    }

    /**
     * Counts products sold by a specific seller.
     *
     * @param sellerId The ID of the seller.
     * @return A Mono emitting the count of products for the seller.
     */
    public Mono<Long> countProductsBySeller(Long sellerId) {
        log.info("Counting products for seller ID {}", sellerId);
        return productRepository.countBySellerId(sellerId)
                .doOnSuccess(count -> log.info("Total product count for seller {}: {}", sellerId, count))
                .doOnError(e -> log.error("Error counting products for seller {}: {}", sellerId, e.getMessage(), e));
    }

    // --- Products by Category ---

    /**
     * Finds products by their category with pagination, enriching each.
     *
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     */
    public Flux<Product> getProductsByCategory(String category, int page, int size) {
        log.info("Retrieving products for category '{}' with page {} and size {}", category, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByCategory(category, pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for category '{}' for page {} with size {}.", category, page, size))
                .doOnError(e -> log.error("Error retrieving products for category {}: {}", category, e.getMessage(), e));
    }

    /**
     * Counts products by their category.
     *
     * @param category The category name.
     * @return A Mono emitting the count of products for the category.
     */
    public Mono<Long> countProductsByCategory(String category) {
        log.info("Counting products for category '{}'", category);
        return productRepository.countByCategory(category)
                .doOnSuccess(count -> log.info("Total product count for category '{}': {}", category, count))
                .doOnError(e -> log.error("Error counting products for category {}: {}", category, e.getMessage(), e));
    }

    // --- Products by Price Range ---

    /**
     * Finds products within a given price range (inclusive) with pagination, enriching each.
     *
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     */
    public Flux<Product> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        log.info("Retrieving products in price range [{}, {}] with page {} and size {}", minPrice, maxPrice, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByPriceBetween(minPrice, maxPrice, pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for price range [{}, {}] for page {} with size {}.", minPrice, maxPrice, page, size))
                .doOnError(e -> log.error("Error retrieving products by price range: {}", e.getMessage(), e));
    }

    /**
     * Counts products within a given price range (inclusive).
     *
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @return A Mono emitting the count of products within the price range.
     */
    public Mono<Long> countProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        log.info("Counting products in price range [{}, {}]", minPrice, maxPrice);
        return productRepository.countByPriceBetween(minPrice, maxPrice)
                .doOnSuccess(count -> log.info("Total product count for price range [{}, {}]: {}", minPrice, maxPrice, count))
                .doOnError(e -> log.error("Error counting products by price range: {}", e.getMessage(), e));
    }

    // --- Combined Filters ---

    /**
     * Finds products in a specific store and category with pagination, enriching each.
     *
     * @param storeId The ID of the store.
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     * @throws ResourceNotFoundException if the store is not found.
     */
    public Flux<Product> getProductsByStoreAndCategory(Long storeId, String category, int page, int size) {
        log.info("Retrieving products for store ID {} and category '{}' with page {} and size {}", storeId, category, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return storeService.getStoreById(storeId) // Ensure store exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + storeId)))
                .flatMapMany(store -> productRepository.findByStoreIdAndCategory(storeId, category, pageable))
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for store ID {} and category '{}' for page {} with size {}.", storeId, category, page, size))
                .doOnError(e -> log.error("Error retrieving products for store {} and category {}: {}", storeId, category, e.getMessage(), e));
    }

    /**
     * Counts products in a specific store and category.
     *
     * @param storeId The ID of the store.
     * @param category The category name.
     * @return A Mono emitting the count of products.
     * @throws ResourceNotFoundException if the store is not found.
     */
    public Mono<Long> countProductsByStoreAndCategory(Long storeId, String category) {
        log.info("Counting products for store ID {} and category '{}'", storeId, category);
        return storeService.getStoreById(storeId) // Ensure store exists
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.STORE_NOT_FOUND + storeId)))
                .flatMap(store -> productRepository.countByStoreIdAndCategory(storeId, category))
                .doOnSuccess(count -> log.info("Total product count for store {} and category '{}': {}", storeId, category, count))
                .doOnError(e -> log.error("Error counting products for store {} and category {}: {}", storeId, category, e.getMessage(), e));
    }

    /**
     * Finds products by a seller within a specific category with pagination, enriching each.
     *
     * @param sellerId The ID of the seller.
     * @param category The category name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     */
    public Flux<Product> getProductsBySellerAndCategory(Long sellerId, String category, int page, int size) {
        log.info("Retrieving products for seller ID {} and category '{}' with page {} and size {}", sellerId, category, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findBySellerIdAndCategory(sellerId, category, pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for seller ID {} and category '{}' for page {} with size {}.", sellerId, category, page, size))
                .doOnError(e -> log.error("Error retrieving products for seller {} and category {}: {}", sellerId, category, e.getMessage(), e));
    }

    // No direct count for sellerIdAndCategory in provided repository, but can be added if needed.

    /**
     * Finds products by category and within a price range with pagination, enriching each.
     *
     * @param category The category name.
     * @param minPrice The minimum price.
     * @param maxPrice The maximum price.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     */
    public Flux<Product> getProductsByCategoryAndPriceBetween(String category, BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        log.info("Retrieving products for category '{}' and price range [{}, {}] with page {} and size {}", category, minPrice, maxPrice, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByCategoryAndPriceBetween(category, minPrice, maxPrice, pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for category '{}' and price range [{}, {}] for page {} with size {}.", category, minPrice, maxPrice, page, size))
                .doOnError(e -> log.error("Error retrieving products for category {} and price range [{},{}]: {}", category, minPrice, maxPrice, e.getMessage(), e));
    }

    // No direct count for categoryAndPriceBetween in provided repository.

    // --- Search Products (by name) ---

    /**
     * Searches products by name (case-insensitive, contains) with pagination, enriching each.
     *
     * @param productName The product name to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching products (enriched).
     */
    public Flux<Product> searchProducts(String productName, int page, int size) {
        log.info("Searching products for name '{}' with page {} and size {}", productName, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending()); // Assuming sort by name is desired for search
        return productRepository.findByNameContainingIgnoreCase(productName, pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished searching products for name '{}' for page {} with size {}.", productName, page, size))
                .doOnError(e -> log.error("Error searching products for name {}: {}", productName, e.getMessage(), e));
    }

    /**
     * Counts products by name (case-insensitive, contains).
     *
     * @param productName The product name to search for.
     * @return A Mono emitting the count of matching products.
     */
    public Mono<Long> countSearchProducts(String productName) {
        log.info("Counting search results for product name '{}'", productName);
        return productRepository.countByNameContainingIgnoreCase(productName)
                .doOnSuccess(count -> log.info("Total search result count for name '{}': {}", productName, count))
                .doOnError(e -> log.error("Error counting search results for name {}: {}", productName, e.getMessage(), e));
    }

    // --- New Location-based methods ---

    /**
     * Finds products by location ID with pagination, enriching each.
     * Assumes ProductRepository has a method like `findProductsByLocationId(Long locationId, Pageable pageable)`.
     *
     * @param locationId The ID of the location.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     */
    public Flux<Product> getProductsByLocationId(Long locationId, int page, int size) {
        log.info("Retrieving products for location ID {} with page {} and size {}", locationId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findProductsByLocationId(locationId, pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for location ID {} for page {} with size {}.", locationId, page, size))
                .doOnError(e -> log.error("Error retrieving products for location {}: {}", locationId, e.getMessage(), e));
    }

    /**
     * Counts products by location ID.
     * Assumes ProductRepository has a method like `countProductsByLocationId(Long locationId)`.
     *
     * @param locationId The ID of the location.
     * @return A Mono emitting the count of products.
     */
    public Mono<Long> countProductsByLocationId(Long locationId) {
        log.info("Counting products for location ID {}", locationId);
        return productRepository.countProductsByLocationId(locationId)
                .doOnSuccess(count -> log.info("Total product count for location {}: {}", locationId, count))
                .doOnError(e -> log.error("Error counting products for location {}: {}", locationId, e.getMessage(), e));
    }

    /**
     * Finds products by country and city with pagination, enriching each.
     * Assumes ProductRepository has a method like `findProductsByCountryAndCity(String country, String city, Pageable pageable)`.
     *
     * @param country The country name.
     * @param city The city name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting products (enriched).
     */
    public Flux<Product> getProductsByCountryAndCity(String country, String city, int page, int size) {
        log.info("Retrieving products for country '{}', city '{}' with page {} and size {}", country, city, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findProductsByCountryAndCity(country, city, pageable)
                .flatMap(this::prepareDto) // Enrich each product
                .doOnComplete(() -> log.info("Finished retrieving products for country '{}', city '{}' for page {} with size {}.", country, city, page, size))
                .doOnError(e -> log.error("Error retrieving products for country {} and city {}: {}", country, city, e.getMessage(), e));
    }

    /**
     * Counts products by country and city.
     * Assumes ProductRepository has a method like `countProductsByCountryAndCity(String country, String city)`.
     *
     * @param country The country name.
     * @param city The city name.
     * @return A Mono emitting the count of products.
     */
    public Mono<Long> countProductsByCountryAndCity(String country, String city) {
        log.info("Counting products for country '{}', city '{}'", country, city);
        return productRepository.countProductsByCountryAndCity(country, city)
                .doOnSuccess(count -> log.info("Total product count for country '{}', city '{}': {}", country, city, count))
                .doOnError(e -> log.error("Error counting products for country {} and city {}: {}", country, city, e.getMessage(), e));
    }
}
