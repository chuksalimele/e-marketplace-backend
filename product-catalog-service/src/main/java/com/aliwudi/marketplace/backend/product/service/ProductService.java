package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.model.Store;
import com.aliwudi.marketplace.backend.product.model.Category;
import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.repository.CategoryRepository;
import com.aliwudi.marketplace.backend.product.repository.ProductRepository;
import com.aliwudi.marketplace.backend.product.repository.SellerRepository;
import com.aliwudi.marketplace.backend.product.repository.StoreRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page; // NEW IMPORT
import org.springframework.data.domain.Pageable; // NEW IMPORT
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;                
    private final StoreRepository storeRepository; // Inject the new StoreRepository

// Update constructor to include StoreRepository
@Autowired
public ProductService(ProductRepository productRepository,
                      CategoryRepository categoryRepository,
                      SellerRepository sellerRepository, // May still be needed for other ops or to get a seller for a store
                      StoreRepository storeRepository) {
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.sellerRepository = sellerRepository;
    this.storeRepository = storeRepository;
}

@Transactional
public Product createProduct(ProductRequest productRequest) {
    // Find the Store, not the Seller directly
    Store store = storeRepository.findById(productRequest.getStoreId())
            .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + productRequest.getStoreId()));

    Category category = categoryRepository.findByName(productRequest.getCategoryName())
            .orElseThrow(() -> new ResourceNotFoundException("Category", "name", productRequest.getCategoryName()));

    Product product = new Product();
    product.setName(productRequest.getName());
    product.setDescription(productRequest.getDescription());
    product.setPrice(productRequest.getPrice());
    product.setStock(productRequest.getStock());
    product.setCategory(category);
    product.setStore(store); // Link to Store

    return productRepository.save(product);
}

@Transactional
public Product updateProduct(Long id, ProductRequest productRequest) {
    Product existingProduct = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

    Category category = categoryRepository.findByName(productRequest.getCategoryName())
            .orElseThrow(() -> new ResourceNotFoundException("Category", "name", productRequest.getCategoryName()));

    // Find the Store
    Store store = storeRepository.findById(productRequest.getStoreId())
            .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + productRequest.getStoreId()));

    existingProduct.setName(productRequest.getName());
    existingProduct.setDescription(productRequest.getDescription());
    existingProduct.setPrice(productRequest.getPrice());
    existingProduct.setStock(productRequest.getStock());
    existingProduct.setCategory(category);
    existingProduct.setStore(store); // Link to Store

    return productRepository.save(existingProduct);
}


    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productRepository.delete(product);
    }

    // MODIFIED: getAllProducts to accept Pageable
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
    // MODIFIED: getAllProducts to accept Pageable
    public Page<Product> getAllProductsByLocation(String location, Pageable pageable) {
        return productRepository.findByStore_LocationIgnoreCase(location, pageable);
    }

    // NEW: Get products by category with pagination and sorting
    public Page<Product> getProductsByCategory(String categoryName, Pageable pageable) {
        return productRepository.findByCategory_Name(categoryName, pageable);
    }
    
    public Page<Product> getProductsByCategoryAndLocation(String categoryName, String location, Pageable pageable) {
        return productRepository.findByCategory_NameAndStore_Location(categoryName, location, pageable);
    }
    
    //getProductsByStoreAndLocation
    public Page<Product> getProductsByStoreAndLocation(Long storeId, String location, Pageable pageable) {
        return productRepository.findByStore_IdAndStore_LocationIgnoreCase(storeId, location, pageable);
    }

    // NEW: Get products by store with pagination and sorting
    public Page<Product> getProductsByStore(Long storeId, Pageable pageable) {
        return productRepository.findByStore_Id(storeId, pageable);
    }

    // NEW: Search products by name or description with pagination and sorting
    public Page<Product> searchProducts(String searchTerm, Pageable pageable) {    
        return productRepository.findByNameContainingIgnoreCase(searchTerm, pageable);
    }    
    
    public Page<Product> searchProductsByNameAndLocation(String product_name, String location, Pageable pageable) {        
        return productRepository.findByNameContainingIgnoreCaseAndStore_LocationIgnoreCase(product_name, location, pageable);
    } 
    
   }