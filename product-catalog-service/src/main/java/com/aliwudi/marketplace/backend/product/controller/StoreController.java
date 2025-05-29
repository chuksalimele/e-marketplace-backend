package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.product.dto.StoreRequest;
import com.aliwudi.marketplace.backend.product.model.Store;
import com.aliwudi.marketplace.backend.product.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
// Removed Page and Pageable imports as they are not typically used with reactive repositories
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive single results
import java.util.List; // Still useful for collecting Flux into a List
import org.springframework.web.server.ResponseStatusException; // For reactive error handling

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService storeService;

    @Autowired
    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')") // Only sellers/admins can create stores
    public Mono<ResponseEntity<Store>> createStore(@Valid @RequestBody StoreRequest storeRequest) {
        return storeService.createStore(storeRequest)
                .map(createdStore -> new ResponseEntity<>(createdStore, HttpStatus.CREATED))
                .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
    }

    @GetMapping
    public Mono<ResponseEntity<List<Store>>> getAllStores() {
        return storeService.getAllStores()
                .collectList() // Collect Flux of stores into a List
                .map(stores -> new ResponseEntity<>(stores, HttpStatus.OK));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Store>> getStoreById(@PathVariable Long id) {
        return storeService.getStoreById(id)
                .map(store -> new ResponseEntity<>(store, HttpStatus.OK))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // Handle not found case
    }

    // MODIFIED: getStoresBySeller to use reactive pagination parameters
    @GetMapping("/by-seller/{sellerId}") // Changed path to avoid ambiguity with getStoreById
    public Mono<ResponseEntity<List<Store>>> getStoresBySeller(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") Long offset, // Reactive pagination: offset
            @RequestParam(defaultValue = "20") Integer limit) { // Reactive pagination: limit

        return storeService.getStoresBySeller(sellerId, offset, limit)
                .collectList() // Collect Flux of stores into a List
                .map(stores -> new ResponseEntity<>(stores, HttpStatus.OK));
    }

    // NEW: Endpoint to get the total count of stores for a specific seller
    @GetMapping("/by-seller/{sellerId}/count")
    public Mono<ResponseEntity<Long>> countStoresBySeller(@PathVariable Long sellerId) {
        return storeService.countStoresBySeller(sellerId)
                .map(count -> new ResponseEntity<>(count, HttpStatus.OK));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public Mono<ResponseEntity<Store>> updateStore(@PathVariable Long id, @Valid @RequestBody StoreRequest storeRequest) {
        return storeService.updateStore(id, storeRequest)
                .map(updatedStore -> new ResponseEntity<>(updatedStore, HttpStatus.OK))
                .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    @ResponseStatus(HttpStatus.NO_CONTENT) // Sets 204 No Content on successful deletion
    public Mono<Void> deleteStore(@PathVariable Long id) {
        return storeService.deleteStore(id)
                .onErrorResume(e -> Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting store", e)));
    }
}