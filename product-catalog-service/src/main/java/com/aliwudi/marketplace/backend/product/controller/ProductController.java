package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.product.dto.ProductRequest;
import com.aliwudi.marketplace.backend.product.model.Product;
import com.aliwudi.marketplace.backend.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
// Remove Page and Pageable imports
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive single results

// Remove List import

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
    public Mono<ResponseEntity<Product>> createProduct(@Valid @RequestBody ProductRequest productRequest) {
        return productService.createProduct(productRequest)
                .map(createdProduct -> new ResponseEntity<>(createdProduct, HttpStatus.CREATED));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<ResponseEntity<Product>> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest productRequest) {
        return productService.updateProduct(id, productRequest)
                .map(updatedProduct -> new ResponseEntity<>(updatedProduct, HttpStatus.OK));
    }

    @GetMapping
    public Mono<ResponseEntity<List<Product>>> getAllProducts(
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") Long offset, // Reactive pagination: offset
            @RequestParam(defaultValue = "20") Integer limit) { // Reactive pagination: limit
        Flux<Product> productsFlux;

        if (location != null && !location.trim().isEmpty()) {
            productsFlux = productService.getAllProductsByLocation(location, offset, limit);
        } else {
            productsFlux = productService.getAllProducts(offset, limit);
        }

        // Collect Flux into a List and wrap in ResponseEntity
        return productsFlux.collectList()
                .map(products -> new ResponseEntity<>(products, HttpStatus.OK));
    }

    // NEW: Endpoint to get total count for all products (useful for pagination metadata)
    @GetMapping("/count")
    public Mono<ResponseEntity<Long>> countAllProducts(@RequestParam(required = false) String location) {
        Mono<Long> countMono;
        if (location != null && !location.trim().isEmpty()) {
            countMono = productService.countProductsByLocation(location);
        } else {
            countMono = productService.countAllProducts();
        }
        return countMono.map(count -> new ResponseEntity<>(count, HttpStatus.OK));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Product>> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> new ResponseEntity<>(product, HttpStatus.OK));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @ResponseStatus(HttpStatus.NO_CONTENT) // This will set the status code for success
    public Mono<Void> deleteProduct(@PathVariable Long id) {
        return productService.deleteProduct(id);
    }

    @GetMapping("/category/{categoryName}")
    public Mono<ResponseEntity<List<Product>>> getProductsByCategory(
            @PathVariable String categoryName,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {
        Flux<Product> productsFlux;

        if (location != null && !location.trim().isEmpty()) {
            productsFlux = productService.getProductsByCategoryAndLocation(categoryName, location, offset, limit);
        } else {
            productsFlux = productService.getProductsByCategory(categoryName, offset, limit);
        }

        return productsFlux.collectList()
                .map(products -> new ResponseEntity<>(products, HttpStatus.OK));
    }

    // NEW: Endpoint to get total count for products by category
    @GetMapping("/category/{categoryName}/count")
    public Mono<ResponseEntity<Long>> countProductsByCategory(
            @PathVariable String categoryName,
            @RequestParam(required = false) String location) {
        Mono<Long> countMono;
        if (location != null && !location.trim().isEmpty()) {
            countMono = productService.countProductsByCategoryAndLocation(categoryName, location);
        } else {
            countMono = productService.countProductsByCategory(categoryName);
        }
        return countMono.map(count -> new ResponseEntity<>(count, HttpStatus.OK));
    }

    @GetMapping("/store/{storeId}")
    public Mono<ResponseEntity<List<Product>>> getProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {
        Flux<Product> productsFlux;

        if (location != null && !location.trim().isEmpty()) {
            productsFlux = productService.getProductsByStoreAndLocation(storeId, location, offset, limit);
        } else {
            productsFlux = productService.getProductsByStore(storeId, offset, limit);
        }

        return productsFlux.collectList()
                .map(products -> new ResponseEntity<>(products, HttpStatus.OK));
    }

    // NEW: Endpoint to get total count for products by store
    @GetMapping("/store/{storeId}/count")
    public Mono<ResponseEntity<Long>> countProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(required = false) String location) {
        Mono<Long> countMono;
        if (location != null && !location.trim().isEmpty()) {
            countMono = productService.countProductsByStoreAndLocation(storeId, location);
        } else {
            countMono = productService.countProductsByStore(storeId);
        }
        return countMono.map(count -> new ResponseEntity<>(count, HttpStatus.OK));
    }

    @GetMapping("/search")
    public Mono<ResponseEntity<List<Product>>> searchProducts(
            @RequestParam String product_name,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {
        Flux<Product> productsFlux;

        if (location != null && !location.trim().isEmpty()) {
            productsFlux = productService.searchProductsByNameAndLocation(product_name, location, offset, limit);
        } else {
            productsFlux = productService.searchProducts(product_name, offset, limit);
        }

        return productsFlux.collectList()
                .map(products -> new ResponseEntity<>(products, HttpStatus.OK));
    }

    // NEW: Endpoint to get total count for search results
    @GetMapping("/search/count")
    public Mono<ResponseEntity<Long>> countSearchProducts(
            @RequestParam String product_name,
            @RequestParam(required = false) String location) {
        Mono<Long> countMono;
        if (location != null && !location.trim().isEmpty()) {
            countMono = productService.countSearchProductsByNameAndLocation(product_name, location);
        } else {
            countMono = productService.countSearchProducts(product_name);
        }
        return countMono.map(count -> new ResponseEntity<>(count, HttpStatus.OK));
    }
}