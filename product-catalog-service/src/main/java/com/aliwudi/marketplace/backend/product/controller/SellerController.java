package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.product.model.Seller;
import com.aliwudi.marketplace.backend.product.service.SellerService;
import org.springframework.beans.factory.annotation.Autowired;
// Remove Page, Pageable, Sort, PageableDefault, SortDefault imports
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive single results

import java.util.List; // Keep this import for collecting Flux into a List
// Remove Optional import

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600) // Adjust for Flutter app's port
@RestController
@RequestMapping("/api/sellers")
public class SellerController {

    // Removed direct SellerRepository injection from controller as service layer handles it.
    private final SellerService sellerService;

    @Autowired
    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    // Get all sellers - accessible by any authenticated user
    // MODIFIED: getAllSellers to support reactive pagination and sorting
    // Example usage: GET /api/sellers?offset=0&limit=10
    @GetMapping
    public Mono<ResponseEntity<List<Seller>>> getAllSellers(
            @RequestParam(defaultValue = "0") Long offset, // Reactive pagination: offset
            @RequestParam(defaultValue = "10") Integer limit) { // Reactive pagination: limit

        // Collect Flux into a List and wrap in ResponseEntity
        return sellerService.getAllSellers(offset, limit)
                .collectList()
                .map(sellers -> new ResponseEntity<>(sellers, HttpStatus.OK));
    }

    // NEW: Endpoint to get total count for all sellers (useful for pagination metadata)
    @GetMapping("/count")
    public Mono<ResponseEntity<Long>> countAllSellers() {
        return sellerService.countAllSellers()
                .map(count -> new ResponseEntity<>(count, HttpStatus.OK));
    }

    // Get seller by ID - accessible by any authenticated user
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Seller>> getSellerById(@PathVariable Long id) {
        return sellerService.getSellerById(id) // Use service, which returns Mono<Seller>
                .map(seller -> new ResponseEntity<>(seller, HttpStatus.OK))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // Handle not found
    }

    // Create a new seller - Only for ADMINS
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Seller>> createSeller(@RequestBody Seller seller) {
        return sellerService.saveSeller(seller) // Use service, which returns Mono<Seller>
                .map(_seller -> new ResponseEntity<>(_seller, HttpStatus.CREATED))
                .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Handle errors reactively
    }

    // Update an existing seller - Only for ADMINS
    // Updates only the basic seller information e.g name
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Seller>> updateSeller(@PathVariable Long id, @RequestBody Seller seller) {
        // Retrieve the existing seller, update it, and save reactively
        return sellerService.getSellerById(id)
                .switchIfEmpty(Mono.just(new Seller())) // Provide an empty Mono if not found to fall into defaultIfEmpty
                .flatMap(existingSeller -> {
                    if (existingSeller.getId() == null) { // Check if it was actually found
                        return Mono.just(new ResponseEntity<>(HttpStatus.NOT_FOUND));
                    }
                    existingSeller.setName(seller.getName());
                    // _seller.setEmail(seller.getEmail());//email is not updatable
                    return sellerService.saveSeller(existingSeller)
                            .map(updatedSeller -> new ResponseEntity<>(updatedSeller, HttpStatus.OK));
                })
                .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    // Delete a seller - Only for ADMINS
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT) // Indicate success with 204 No Content
    public Mono<Void> deleteSeller(@PathVariable Long id) {
        return sellerService.deleteSeller(id) // Use service, which returns Mono<Void>
                .onErrorResume(e -> Mono.error(new org.springframework.web.server.ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting seller", e))); // Handle errors reactively
    }

    // NEW: Endpoint to search sellers by name with reactive pagination and sorting
    // Example: GET /api/sellers/search?query=tech&offset=0&limit=5
    @GetMapping("/search")
    public Mono<ResponseEntity<List<Seller>>> searchSellers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "10") Integer limit) {

        return sellerService.searchSellers(query, offset, limit)
                .collectList()
                .map(sellers -> new ResponseEntity<>(sellers, HttpStatus.OK));
    }

    // NEW: Endpoint to get total count for search results pagination metadata
    @GetMapping("/search/count")
    public Mono<ResponseEntity<Long>> countSearchSellers(@RequestParam String query) {
        return sellerService.countSearchSellers(query)
                .map(count -> new ResponseEntity<>(count, HttpStatus.OK));
    }
}