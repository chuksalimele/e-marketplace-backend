package com.marketplace.emarketplacebackend.service;

import com.marketplace.emarketplacebackend.dto.StoreRequest;
import com.marketplace.emarketplacebackend.exception.ResourceNotFoundException;
import com.marketplace.emarketplacebackend.model.Seller;
import com.marketplace.emarketplacebackend.model.Store;
import com.marketplace.emarketplacebackend.repository.SellerRepository;
import com.marketplace.emarketplacebackend.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page; // NEW IMPORT
import org.springframework.data.domain.Pageable; // NEW IMPORT

import java.util.List;
import java.util.Optional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final SellerRepository sellerRepository; // To link stores to sellers

    @Autowired
    public StoreService(StoreRepository storeRepository, SellerRepository sellerRepository) {
        this.storeRepository = storeRepository;
        this.sellerRepository = sellerRepository;
    }

    @Transactional
    public Store createStore(StoreRequest storeRequest) {
        Seller seller = sellerRepository.findById(storeRequest.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + storeRequest.getSellerId()));

        Store store = new Store();
        store.setName(storeRequest.getName());
        store.setLocation(storeRequest.getLocation());
        store.setDescription(storeRequest.getDescription());
        store.setContactInfo(storeRequest.getContactInfo());
        store.setProfileImageUrl(storeRequest.getProfileImageUrl());
        store.setRating(storeRequest.getRating());
        store.setCategories(storeRequest.getCategories());
        store.setSeller(seller);

        return storeRepository.save(store);
    }

    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }

    public Optional<Store> getStoreById(Long id) {
        return storeRepository.findById(id);
    }

    public Page<Store> getStoresBySeller(Long sellerId, Pageable pageable) {
        return storeRepository.findBySeller_Id(sellerId, pageable);
    }

    // Add update and delete methods for Store
    @Transactional
    public Store updateStore(Long id, StoreRequest storeRequest) {
        Store existingStore = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + id));

        Seller seller = sellerRepository.findById(storeRequest.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with id: " + storeRequest.getSellerId()));

        existingStore.setName(storeRequest.getName());
        existingStore.setLocation(storeRequest.getLocation());
        existingStore.setDescription(storeRequest.getDescription());
        existingStore.setContactInfo(storeRequest.getContactInfo());
        existingStore.setProfileImageUrl(storeRequest.getProfileImageUrl());
        existingStore.setRating(storeRequest.getRating());
        existingStore.setCategories(storeRequest.getCategories());
        existingStore.setSeller(seller);

        return storeRepository.save(existingStore);
    }

    @Transactional
    public void deleteStore(Long id) {
        if (!storeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Store not found with id: " + id);
        }
        storeRepository.deleteById(id);
    }
}