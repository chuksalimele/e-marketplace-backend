package com.aliwudi.marketplace.backend.product.controller; // Assuming 'seller' is part of 'product' for now

import com.aliwudi.marketplace.backend.common.model.Seller;
import com.aliwudi.marketplace.backend.product.dto.SellerRequest; // New DTO for incoming seller data
import com.aliwudi.marketplace.backend.product.exception.DuplicateResourceException; // Re-using or creating
import com.aliwudi.marketplace.backend.product.exception.InvalidSellerDataException; // New custom exception for seller-specific data issues
import com.aliwudi.marketplace.backend.product.exception.ResourceNotFoundException; // Re-using or creating
import com.aliwudi.marketplace.backend.product.service.SellerService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600) // Adjust for Flutter app's port
@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor // Using Lombok for constructor injection
public class SellerController {

    private final SellerService sellerService;

    /**
     * Helper method to map Seller entity to Seller DTO for public exposure.
     */
    private Seller prepareDto(Seller seller) {
        if (seller == null) {
            return null;
        }
        return seller;
    }

    @GetMapping
    public Mono<StandardResponseEntity> getAllSellers(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        if (page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return sellerService.getAllSellers(page, size)
                .map(this::prepareDto)
                .collectList()
                .map(sellerResponses -> (StandardResponseEntity) StandardResponseEntity.ok(sellerResponses, ApiResponseMessages.SELLERS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_SELLERS + ": " + e.getMessage())));
    }

    @GetMapping("/count")
    public Mono<StandardResponseEntity> countAllSellers() {
        return sellerService.countAllSellers()
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.SELLER_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_SELLER_COUNT + ": " + e.getMessage())));
    }

    @GetMapping("/{id}")
    public Mono<StandardResponseEntity> getSellerById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SELLER_ID));
        }

        return sellerService.getSellerById(id)
                .map(seller -> (StandardResponseEntity) StandardResponseEntity.ok(prepareDto(seller), ApiResponseMessages.SELLER_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_SELLER + ": " + e.getMessage())));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> createSeller(@Valid @RequestBody SellerRequest sellerRequest) {
        // Basic input validation
        if (sellerRequest.getName() == null || sellerRequest.getName().isBlank() ||
            sellerRequest.getEmail() == null || sellerRequest.getEmail().isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SELLER_CREATION_REQUEST));
        }

        return sellerService.createSeller(sellerRequest)
                .map(createdSeller -> (StandardResponseEntity) StandardResponseEntity.created(prepareDto(createdSeller), ApiResponseMessages.SELLER_CREATED_SUCCESS))
                .onErrorResume(DuplicateResourceException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.DUPLICATE_SELLER_EMAIL)))
                .onErrorResume(InvalidSellerDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CREATING_SELLER + ": " + e.getMessage())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> updateSeller(@PathVariable Long id, @Valid @RequestBody SellerRequest sellerRequest) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SELLER_ID));
        }
        if (sellerRequest.getName() == null || sellerRequest.getName().isBlank()) {
             return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SELLER_UPDATE_REQUEST));
        }

        return sellerService.updateSeller(id, sellerRequest)
                .map(updatedSeller -> (StandardResponseEntity) StandardResponseEntity.ok(prepareDto(updatedSeller), ApiResponseMessages.SELLER_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .onErrorResume(InvalidSellerDataException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_SELLER + ": " + e.getMessage())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> deleteSeller(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SELLER_ID));
        }

        return sellerService.deleteSeller(id)
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.SELLER_DELETED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.SELLER_NOT_FOUND + id)))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_SELLER + ": " + e.getMessage())));
    }

    @GetMapping("/search")
    public Mono<StandardResponseEntity> searchSellers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        if (query == null || query.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }
        if (page < 0 || size <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return sellerService.searchSellers(query, page, size)
                .map(this::prepareDto)
                .collectList()
                .map(sellerResponses -> (StandardResponseEntity) StandardResponseEntity.ok(sellerResponses, ApiResponseMessages.SELLERS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_SELLERS + ": " + e.getMessage())));
    }

    @GetMapping("/search/count")
    public Mono<StandardResponseEntity> countSearchSellers(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_SEARCH_TERM));
        }

        return sellerService.countSearchSellers(query)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.SELLER_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_SEARCHING_SELLER_COUNT + ": " + e.getMessage())));
    }
}