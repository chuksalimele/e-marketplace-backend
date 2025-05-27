// ProductController.java
package com.aliwudi.marketplace.backend.product.controller;

import com.marketplace.emarketplacebackend.dto.ProductRequest;
import com.marketplace.emarketplacebackend.model.Product;
import com.marketplace.emarketplacebackend.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List; // Keep this if other methods return List, or remove if all return Page

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        Product createdProduct = productService.createProduct(productRequest);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest productRequest) {
        Product updatedProduct = productService.updateProduct(id, productRequest);
        return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    }

    // MODIFIED: getAllProducts to support pagination, sorting, and optional location filter
    // Example usage: GET /api/products?page=0&size=10&sort=name,asc&location=NewYork
    // Default: page=0, size=20, sort by id ascending
    @GetMapping
    public ResponseEntity<Page<Product>> getAllProducts(
            @RequestParam(required = false) String location, // Optional location parameter
            @PageableDefault(page = 0, size = 20) // Default page 0, size 20
            @SortDefault(sort = "id", direction = Sort.Direction.ASC) // Default sort by id ascending
            Pageable pageable) {

        Page<Product> products;
        if (location != null && !location.trim().isEmpty()) {
            products = productService.getAllProductsByLocation(location, pageable);
        } else {
            products = productService.getAllProducts(pageable);
        }
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return new ResponseEntity<>(product, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    // MODIFIED: Endpoint to get products by category with pagination, sorting, and optional location
    // Example usage: GET /api/products/category/Electronics?location=London&page=0&size=10
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<Page<Product>> getProductsByCategory(
            @PathVariable String categoryName,
            @RequestParam(required = false) String location, // NEW: Optional location parameter
            @PageableDefault(page = 0, size = 20)
            @SortDefault(sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<Product> products;
        if (location != null && !location.trim().isEmpty()) {
            // Call a new service method that handles both category and location
            products = productService.getProductsByCategoryAndLocation(categoryName, location, pageable);
        } else {
            // Existing behavior: search by category only
            products = productService.getProductsByCategory(categoryName, pageable);
        }
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    // MODIFIED: Endpoint to get products by seller store with pagination, sorting, and optional location
    // Example usage: GET /api/products/seller/store/123?location=Paris&page=0&size=10
    @GetMapping("/store/{storeId}")
    public ResponseEntity<Page<Product>> getProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(required = false) String location, // NEW: Optional location parameter
            @PageableDefault(page = 0, size = 20)
            @SortDefault(sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<Product> products;
        if (location != null && !location.trim().isEmpty()) {
            // Call a new service method that handles both store and location
            products = productService.getProductsByStoreAndLocation(storeId, location, pageable);
        } else {
            // Existing behavior: search by seller only
            products = productService.getProductsByStore(storeId, pageable);
        }
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    // MODIFIED: Endpoint to search products by query AND optional location
    // Example usage: GET /api/products/search?query=laptop&location=NewYork&page=0&size=10
    @GetMapping("/search")
    public ResponseEntity<Page<Product>> searchProducts(
            @RequestParam String product_name,
            @RequestParam(required = false) String location, // NEW: Optional location parameter
            @PageableDefault(page = 0, size = 20)
            @SortDefault(sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<Product> products;
        if (location != null && !location.trim().isEmpty()) {
            // Call a new service method that handles both query and location
            products = productService.searchProductsByNameAndLocation(product_name, location, pageable);
        } else {
            // Existing behavior: search by query only
            products = productService.searchProducts(product_name, pageable);
        }
        return new ResponseEntity<>(products, HttpStatus.OK);
    }
}