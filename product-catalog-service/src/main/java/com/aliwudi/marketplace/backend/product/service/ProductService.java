package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.model.Store;
import com.aliwudi.marketplace.backend.product.model.Category;
import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.repository.CategoryRepository;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository;
import com.aliwudi.marketplace.backend.product.repository.SellerRepository; // Assuming this is also reactive now
import com.aliwudi.marketplace.backend.product.repository.StoreRepository; // Assuming this is also reactive now

import org.springframework.beans.factory.annotation.Autowired;
// Remove Page and Pageable imports
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Keep for reactive transaction management
import reactor.core.publisher.Mono; // NEW: Import Mono for single reactive results
import reactor.core.publisher.Flux; // NEW: Import Flux for multiple reactive results

// Remove Optional import

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;
    private final StoreRepository storeRepository;

    @Autowired
    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          SellerRepository sellerRepository,
                          StoreRepository storeRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.sellerRepository = sellerRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional
    public Mono<Product> createProduct(ProductRequest productRequest) {
        // Find the Store reactively
        Mono<Store> storeMono = storeRepository.findById(productRequest.getStoreId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Store not found with id: " + productRequest.getStoreId())));

        // Find the Category reactively
        Mono<Category> categoryMono = categoryRepository.findByName(productRequest.getCategoryName())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category", "name", productRequest.getCategoryName())));

        // Combine the results and create the product
        return Mono.zip(storeMono, categoryMono)
                .flatMap(tuple -> {
                    Store store = tuple.getT1();
                    Category category = tuple.getT2();

                    Product product = new Product();
                    product.setName(productRequest.getName());
                    product.setDescription(productRequest.getDescription());
                    product.setPrice(productRequest.getPrice());
                    product.setStock(productRequest.getStock());
                    product.setCategory(category);
                    product.setStore(store);

                    return productRepository.save(product);
                });
    }

    @Transactional
    public Mono<Product> updateProduct(Long id, ProductRequest productRequest) {
        Mono<Product> existingProductMono = productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product", "id", id)));

        Mono<Category> categoryMono = categoryRepository.findByName(productRequest.getCategoryName())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category", "name", productRequest.getCategoryName())));

        Mono<Store> storeMono = storeRepository.findById(productRequest.getStoreId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Store not found with id: " + productRequest.getStoreId())));

        return Mono.zip(existingProductMono, categoryMono, storeMono)
                .flatMap(tuple -> {
                    Product existingProduct = tuple.getT1();
                    Category category = tuple.getT2();
                    Store store = tuple.getT3();

                    existingProduct.setName(productRequest.getName());
                    existingProduct.setDescription(productRequest.getDescription());
                    existingProduct.setPrice(productRequest.getPrice());
                    existingProduct.setStock(productRequest.getStock());
                    existingProduct.setCategory(category);
                    existingProduct.setStore(store);

                    return productRepository.save(existingProduct);
                });
    }

    public Mono<Product> getProductById(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with id: " + id)));
    }

    @Transactional
    public Mono<Void> deleteProduct(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with id: " + id)))
                .flatMap(productRepository::delete) // Assuming delete returns Mono<Void>
                .then(); // Convert Mono<Product> (if delete returns it) to Mono<Void>
    }

    // MODIFIED: getAllProducts to accept offset and limit for pagination
    public Flux<Product> getAllProducts(Long offset, Integer limit) {
        return productRepository.findAll(offset, limit);
    }

    // NEW: Get total count for pagination
    public Mono<Long> countAllProducts() {
        return productRepository.count();
    }

    // MODIFIED: getAllProductsByLocation to accept offset and limit
    public Flux<Product> getAllProductsByLocation(String location, Long offset, Integer limit) {
        return productRepository.findByStore_LocationIgnoreCase(location, offset, limit);
    }

    public Mono<Long> countProductsByLocation(String location) {
        return productRepository.countByStore_LocationIgnoreCase(location);
    }

    // NEW: Get products by category with pagination and sorting
    public Flux<Product> getProductsByCategory(String categoryName, Long offset, Integer limit) {
        return productRepository.findByCategory_Name(categoryName, offset, limit);
    }

    public Mono<Long> countProductsByCategory(String categoryName) {
        return productRepository.countByCategory_Name(categoryName);
    }

    public Flux<Product> getProductsByCategoryAndLocation(String categoryName, String location, Long offset, Integer limit) {
        return productRepository.findByCategory_NameAndStore_Location(categoryName, location, offset, limit);
    }

    public Mono<Long> countProductsByCategoryAndLocation(String categoryName, String location) {
        return productRepository.countByCategory_NameAndStore_Location(categoryName, location);
    }

    // getProductsByStoreAndLocation
    public Flux<Product> getProductsByStoreAndLocation(Long storeId, String location, Long offset, Integer limit) {
        return productRepository.findByStore_IdAndStore_LocationIgnoreCase(storeId, location, offset, limit);
    }

    public Mono<Long> countProductsByStoreAndLocation(Long storeId, String location) {
        return productRepository.countByStore_IdAndStore_LocationIgnoreCase(storeId, location);
    }

    // NEW: Get products by store with pagination and sorting
    public Flux<Product> getProductsByStore(Long storeId, Long offset, Integer limit) {
        return productRepository.findByStore_Id(storeId, offset, limit);
    }

    public Mono<Long> countProductsByStore(Long storeId) {
        return productRepository.countByStore_Id(storeId);
    }

    // NEW: Search products by name or description with pagination and sorting
    public Flux<Product> searchProducts(String searchTerm, Long offset, Integer limit) {
        return productRepository.findByNameContainingIgnoreCase(searchTerm, offset, limit);
    }

    public Mono<Long> countSearchProducts(String searchTerm) {
        return productRepository.countByNameContainingIgnoreCase(searchTerm);
    }

    public Flux<Product> searchProductsByNameAndLocation(String product_name, String location, Long offset, Integer limit) {
        return productRepository.findByNameContainingIgnoreCaseAndStore_LocationIgnoreCase(product_name, location, offset, limit);
    }

    public Mono<Long> countSearchProductsByNameAndLocation(String product_name, String location) {
        return productRepository.countByNameContainingIgnoreCaseAndStore_LocationIgnoreCase(product_name, location);
    }
}