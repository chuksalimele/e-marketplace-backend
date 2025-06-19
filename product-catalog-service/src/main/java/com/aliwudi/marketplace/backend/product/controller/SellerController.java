package com.aliwudi.marketplace.backend.product.controller;

import com.aliwudi.marketplace.backend.common.model.Seller;
import com.aliwudi.marketplace.backend.product.dto.SellerRequest;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.InvalidSellerDataException; // Corrected package
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException; // Corrected package
import com.aliwudi.marketplace.backend.product.service.SellerService;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Removed unused imports: java.util.List, java.util.stream.Collectors, com.aliwudi.marketplace.backend.common.model.Store, com.aliwudi.marketplace.backend.product.service.StoreService
// as prepareDto logic moved to service

// Static import for API path constants and roles
import static com.aliwudi.marketplace.backend.common.constants.ApiPaths.*;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600) // Adjust for Flutter app's port
@RestController
@RequestMapping(SELLER_CONTROLLER_BASE) // MODIFIED
@RequiredArgsConstructor // Using Lombok for constructor injection
public class SellerController {

    private final SellerService sellerService;

    /**
     * Endpoint to retrieve all sellers with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux of Seller entities.
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    @GetMapping(SELLER_GET_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Seller> getAllSellers(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        if (page == null || page < 0 || size == null || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        return sellerService.getAllSellers(page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all sellers.
     *
     * @return A Mono emitting the total count of sellers.
     */
    @GetMapping(SELLER_COUNT_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllSellers() {
        return sellerService.countAllSellers();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a seller by their ID.
     *
     * @param id The ID of the seller to retrieve.
     * @return A Mono emitting the Seller.
     * @throws IllegalArgumentException if seller ID is invalid.
     * @throws ResourceNotFoundException if the seller is not found.
     */
    @GetMapping(SELLER_GET_BY_ID) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Seller> getSellerById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SELLER_ID);
        }
        return sellerService.getSellerById(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to create a new seller.
     *
     * @param sellerRequest The DTO containing seller creation data.
     * @return A Mono emitting the created Seller.
     * @throws IllegalArgumentException if input validation fails.
     * @throws DuplicateResourceException if a seller with the same email already exists.
     * @throws InvalidSellerDataException if provided data is invalid.
     */
    @PostMapping(SELLER_CREATE) // MODIFIED
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Mono<Seller> createSeller(@Valid @RequestBody SellerRequest sellerRequest) {
        // Basic input validation
        if (sellerRequest.getName() == null || sellerRequest.getName().isBlank() ||
            sellerRequest.getEmail() == null || sellerRequest.getEmail().isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SELLER_CREATION_REQUEST);
        }
        return sellerService.createSeller(sellerRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update an existing seller.
     *
     * @param id The ID of the seller to update.
     * @param sellerRequest The DTO containing seller update data.
     * @return A Mono emitting the updated Seller.
     * @throws IllegalArgumentException if seller ID or update data is invalid.
     * @throws ResourceNotFoundException if the seller is not found.
     * @throws InvalidSellerDataException if provided data is invalid.
     * @throws DuplicateResourceException if the updated email causes a duplicate (if email update is allowed).
     */
    @PutMapping(SELLER_UPDATE) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Mono<Seller> updateSeller(@PathVariable Long id, @Valid @RequestBody SellerRequest sellerRequest) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SELLER_ID);
        }
        if (sellerRequest.getName() == null || sellerRequest.getName().isBlank()) {
             throw new IllegalArgumentException(ApiResponseMessages.INVALID_SELLER_UPDATE_REQUEST);
        }
        return sellerService.updateSeller(id, sellerRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a seller by their ID.
     *
     * @param id The ID of the seller to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if seller ID is invalid.
     * @throws ResourceNotFoundException if the seller is not found.
     */
    @DeleteMapping(SELLER_DELETE) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED
    public Mono<Void> deleteSeller(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SELLER_ID);
        }
        return sellerService.deleteSeller(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to search sellers by name (case-insensitive, contains) with pagination.
     *
     * @param query The search query for seller name.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @return A Flux emitting matching sellers.
     * @throws IllegalArgumentException if search query or pagination parameters are invalid.
     */
    @GetMapping(SELLER_SEARCH) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Seller> searchSellers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        if (query == null || query.isBlank() || page == null || page < 0 || size == null || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return sellerService.searchSellers(query, page, size);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count sellers by name (case-insensitive, contains).
     *
     * @param query The search query for seller name.
     * @return A Mono emitting the count of matching sellers.
     * @throws IllegalArgumentException if search query is invalid.
     */
    @GetMapping(SELLER_SEARCH_COUNT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countSearchSellers(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return sellerService.countSearchSellers(query);
        // Errors are handled by GlobalExceptionHandler.
    }
}