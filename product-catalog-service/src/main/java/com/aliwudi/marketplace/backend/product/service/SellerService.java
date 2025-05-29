package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.model.Seller;
import com.aliwudi.marketplace.backend.product.repository.SellerRepository;
import org.springframework.beans.factory.annotation.Autowired;
// Remove Page and Pageable imports
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections

// Remove Optional import

@Service
public class SellerService {

    private final SellerRepository sellerRepository;

    @Autowired
    public SellerService(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    // --- CRUD Operations ---

    // MODIFIED: getAllSellers to accept offset and limit for pagination
    public Flux<Seller> getAllSellers(Long offset, Integer limit) {
        // Assuming your reactive SellerRepository now has a findAll method that accepts offset and limit
        return sellerRepository.findAll(offset, limit);
    }

    // NEW: Add a count method for pagination metadata
    public Mono<Long> countAllSellers() {
        return sellerRepository.count();
    }

    public Mono<Seller> getSellerById(Long id) {
        // findById now returns Mono<Seller>
        return sellerRepository.findById(id);
    }

    public Mono<Seller> saveSeller(Seller seller) {
        // save now returns Mono<Seller>
        return sellerRepository.save(seller);
    }

    public Mono<Void> deleteSeller(Long id) {
        // deleteById now returns Mono<Void>
        return sellerRepository.deleteById(id);
    }

    // NEW: Search sellers by name with pagination and sorting
    public Flux<Seller> searchSellers(String searchTerm, Long offset, Integer limit) {
        // Assuming your reactive SellerRepository has a method for searching with pagination
        return sellerRepository.findByNameContainingIgnoreCase(searchTerm, offset, limit);
    }

    // NEW: Add a count method for search results pagination metadata
    public Mono<Long> countSearchSellers(String searchTerm) {
        return sellerRepository.countByNameContainingIgnoreCase(searchTerm);
    }
}