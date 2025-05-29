package com.aliwudi.marketplace.backend.product.service;

import com.aliwudi.marketplace.backend.product.dto.StoreRequest;
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.product.model.Seller;
import com.aliwudi.marketplace.backend.product.model.Store;
import com.aliwudi.marketplace.backend.product.repository.SellerRepository;
import com.aliwudi.marketplace.backend.product.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Remove Page and Pageable imports
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections

// Remove List and Optional imports

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
    public Mono<Store> createStore(StoreRequest storeRequest) {
        // Find the Seller reactively
        return sellerRepository.findById(storeRequest.getSellerId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Seller not found with id: " + storeRequest.getSellerId())))
                .flatMap(seller -> {
                    Store store = new Store();
                    store.setName(storeRequest.getName());
                    store.setLocation(storeRequest.getLocation());
                    store.setDescription(storeRequest.getDescription());
                    store.setContactInfo(storeRequest.getContactInfo());
                    store.setProfileImageUrl(storeRequest.getProfileImageUrl());
                    store.setRating(storeRequest.getRating());
                    store.setCategories(storeRequest.getCategories());
                    store.setSeller(seller); // Link to Seller

                    return storeRepository.save(store); // Save the new store
                });
    }

    public Flux<Store> getAllStores() {
        return storeRepository.findAll(); // findAll now returns Flux<Store>
    }

    public Mono<Store> getStoreById(Long id) {
        return storeRepository.findById(id); // findById now returns Mono<Store>
    }

    // MODIFIED: getStoresBySeller to accept offset and limit for pagination
    public Flux<Store> getStoresBySeller(Long sellerId, Long offset, Integer limit) {
        // Assuming findBySeller_Id in StoreRepository is updated to accept offset and limit
        return storeRepository.findBySeller_Id(sellerId, offset, limit);
    }

    // NEW: Add a count method for pagination metadata
    public Mono<Long> countStoresBySeller(Long sellerId) {
        return storeRepository.countBySeller_Id(sellerId);
    }


    @Transactional
    public Mono<Store> updateStore(Long id, StoreRequest storeRequest) {
        // Find existing store and seller reactively, then update
        Mono<Store> existingStoreMono = storeRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Store not found with id: " + id)));

        Mono<Seller> sellerMono = sellerRepository.findById(storeRequest.getSellerId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Seller not found with id: " + storeRequest.getSellerId())));

        return Mono.zip(existingStoreMono, sellerMono)
                .flatMap(tuple -> {
                    Store existingStore = tuple.getT1();
                    Seller seller = tuple.getT2();

                    existingStore.setName(storeRequest.getName());
                    existingStore.setLocation(storeRequest.getLocation());
                    existingStore.setDescription(storeRequest.getDescription());
                    existingStore.setContactInfo(storeRequest.getContactInfo());
                    existingStore.setProfileImageUrl(storeRequest.getProfileImageUrl());
                    existingStore.setRating(storeRequest.getRating());
                    existingStore.setCategories(storeRequest.getCategories());
                    existingStore.setSeller(seller); // Link to the new seller if changed

                    return storeRepository.save(existingStore); // Save the updated store
                });
    }

    @Transactional
    public Mono<Void> deleteStore(Long id) {
        return storeRepository.existsById(id) // existsById returns Mono<Boolean>
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResourceNotFoundException("Store not found with id: " + id));
                    }
                    return storeRepository.deleteById(id); // deleteById should return Mono<Void>
                });
    }
}