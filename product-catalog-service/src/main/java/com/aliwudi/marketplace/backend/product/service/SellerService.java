// SellerService.java
package com.marketplace.emarketplacebackend.service;

import com.marketplace.emarketplacebackend.model.Seller;
import com.marketplace.emarketplacebackend.repository.SellerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;    // NEW IMPORT
import org.springframework.data.domain.Pageable; // NEW IMPORT
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SellerService {

    private final SellerRepository sellerRepository;

    @Autowired
    public SellerService(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    // --- CRUD Operations ---

    // MODIFIED: getAllSellers to accept Pageable
    public Page<Seller> getAllSellers(Pageable pageable) {
        return sellerRepository.findAll(pageable);
    }

    public Optional<Seller> getSellerById(Long id) {
        return sellerRepository.findById(id);
    }

    public Seller saveSeller(Seller seller) {
        return sellerRepository.save(seller);
    }

    public void deleteSeller(Long id) {
        sellerRepository.deleteById(id);
    }

    // NEW: Search sellers by name with pagination and sorting
    public Page<Seller> searchSellers(String searchTerm, Pageable pageable) {
        return sellerRepository.findByNameContainingIgnoreCase(searchTerm, pageable);
    }
}