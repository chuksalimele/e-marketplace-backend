package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository; // Assuming you have a ProductRepository
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.exception.InvalidProductDataException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    
    public Mono<Product> createProduct(ProductRequest productRequest) {
        // You might add more complex validation here
        if (productRequest.getPrice().doubleValue() < 0 || productRequest.getStockQuantity() < 0) {
            return Mono.error(new InvalidProductDataException("Price and stock quantity cannot be negative."));
        }

        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .stockQuantity(productRequest.getStockQuantity())
                .category(productRequest.getCategory())
                .storeId(productRequest.getStoreId())
                .imageUrl(productRequest.getImageUrl())
                .location(productRequest.getLocation())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return productRepository.save(product);
    }

    
    public Mono<Product> updateProduct(Long id, ProductRequest productRequest) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .flatMap(existingProduct -> {
                    // Update fields from request
                    if (productRequest.getName() != null && !productRequest.getName().isBlank()) {
                        existingProduct.setName(productRequest.getName());
                    }
                    if (productRequest.getDescription() != null && !productRequest.getDescription().isBlank()) {
                        existingProduct.setDescription(productRequest.getDescription());
                    }
                    if (productRequest.getPrice() != null && productRequest.getPrice().doubleValue() >= 0) {
                        existingProduct.setPrice(productRequest.getPrice());
                    } else if (productRequest.getPrice() != null) { // Catch negative price
                        return Mono.error(new InvalidProductDataException("Price cannot be negative."));
                    }
                    if (productRequest.getStockQuantity() != null && productRequest.getStockQuantity() >= 0) {
                        existingProduct.setStockQuantity(productRequest.getStockQuantity());
                    } else if (productRequest.getStockQuantity() != null) { // Catch negative stock
                        return Mono.error(new InvalidProductDataException("Stock quantity cannot be negative."));
                    }
                    if (productRequest.getCategory() != null && !productRequest.getCategory().isBlank()) {
                        existingProduct.setCategory(productRequest.getCategory());
                    }
                    // storeId, imageUrl, location might also be updatable, depending on business rules
                    if (productRequest.getStoreId() != null) {
                         existingProduct.setStoreId(productRequest.getStoreId());
                    }
                    if (productRequest.getImageUrl() != null && !productRequest.getImageUrl().isBlank()) {
                         existingProduct.setImageUrl(productRequest.getImageUrl());
                    }
                     if (productRequest.getLocation() != null && !productRequest.getLocation().isBlank()) {
                         existingProduct.setLocation(productRequest.getLocation());
                    }

                    existingProduct.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(existingProduct);
                });
    }

    
    public Mono<Void> deleteProduct(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)))
                .flatMap(productRepository::delete);
    }

    
    public Mono<Product> getProductById(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.PRODUCT_NOT_FOUND + id)));
    }

    
    public Flux<Product> getAllProducts(Long offset, Integer limit) {
        // Example: Using repository method that supports offset/limit (e.g., Spring Data R2DBC with custom query)        
        return productRepository.findAll(offset, limit);
    }

    
    public Mono<Long> countAllProducts() {
        return productRepository.count();
    }

    
    public Flux<Product> getAllProductsByLocation(String location, Long offset, Integer limit) {
        // Assuming a repository method like findByLocation(String location)
        return productRepository.findByLocation(location, offset, limit);
    }

    
    public Mono<Long> countProductsByLocation(String location) {
        return productRepository.countByLocation(location);
    }

    
    public Flux<Product> getProductsByCategory(String categoryName, Long offset, Integer limit) {
        return productRepository.findByCategory_Name(categoryName, offset, limit);
    }

    
    public Mono<Long> countProductsByCategory(String categoryName) {
        return productRepository.countByCategory_Name(categoryName);
    }

    
    public Flux<Product> getProductsByCategoryAndLocation(String categoryName, String location, Long offset, Integer limit) {
        return productRepository.findByCategory_NameAndLocation(categoryName, location, offset, limit);
    }

    
    public Mono<Long> countProductsByCategoryAndLocation(String categoryName, String location) {
        return productRepository.countByCategory_NameAndLocation(categoryName, location);
    }

    
    public Flux<Product> getProductsByStore(Long storeId, Long offset, Integer limit) {
        return productRepository.findByStore_Id(storeId, offset, limit);
    }

    
    public Mono<Long> countProductsByStore(Long storeId) {
        return productRepository.countByStore_Id(storeId);
    }

    
    public Flux<Product> getProductsByStoreAndLocation(Long storeId, String location, Long offset, Integer limit) {
        return productRepository.findByStore_IdAndLocation(storeId, location, offset, limit);
    }

    
    public Mono<Long> countProductsByStoreAndLocation(Long storeId, String location) {
        return productRepository.countByStore_IdAndLocation(storeId, location);
    }

    
    public Flux<Product> searchProducts(String productName, Long offset, Integer limit) {
        // Assuming a repository method like findByNameContainingIgnoreCase(String name)
        return productRepository.findByNameContainingIgnoreCase(productName, offset, limit);
    }

    
    public Mono<Long> countSearchProducts(String productName) {
        return productRepository.countByNameContainingIgnoreCase(productName);
    }

    
    public Flux<Product> searchProductsByNameAndLocation(String productName, String location, Long offset, Integer limit) {
        return productRepository.findByNameContainingIgnoreCaseAndLocationContainingIgnoreCase(productName, location, offset, limit);
    }

    
    public Mono<Long> countSearchProductsByNameAndLocation(String productName, String location) {
        return productRepository.countByNameContainingIgnoreCaseAndLocationContainingIgnoreCase(productName, location);
    }
}