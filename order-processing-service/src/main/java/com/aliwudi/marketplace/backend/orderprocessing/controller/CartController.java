package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.dto.CartDto; // Assuming CartDto exists
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem; // Assuming CartItem exists
import com.aliwudi.marketplace.backend.orderprocessing.service.CartService;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException; // Assuming product/cart item not found
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException; // For stock-related errors

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor // Use Lombok for constructor injection
public class CartController {

    private final CartService cartService;

    /**
     * Helper method to get the authenticated user's ID from the reactive SecurityContextHolder.
     * This ID is propagated by the API Gateway.
     * @return A Mono emitting the authenticated user's ID.
     * @throws IllegalStateException if the user is not authenticated or ID cannot be retrieved.
     */
    private Mono<Long> getAuthenticatedUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        return Mono.error(new IllegalStateException(ApiResponseMessages.UNAUTHENTICATED_USER));
                    }
                    // Attempt to cast to Long or parse String. Adjust based on your actual principal type.
                    if (authentication.getPrincipal() instanceof Long) {
                        return Mono.just((Long) authentication.getPrincipal());
                    } else if (authentication.getPrincipal() instanceof String) {
                        try {
                            return Mono.just(Long.parseLong((String) authentication.getPrincipal()));
                        } catch (NumberFormatException e) {
                            return Mono.error(new IllegalStateException(ApiResponseMessages.INVALID_USER_ID_FORMAT, e));
                        }
                    }
                    return Mono.error(new IllegalStateException(ApiResponseMessages.INVALID_USER_ID));
                })
                .switchIfEmpty(Mono.error(new IllegalStateException(ApiResponseMessages.SECURITY_CONTEXT_NOT_FOUND)));
    }

    /**
     * Endpoint to add a product to the authenticated user's cart.
     * If the product is already in the cart, its quantity will be updated.
     * Requires the user to be authenticated.
     */
    @PostMapping("/add")
    public Mono<StandardResponseEntity> addProductToCart(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        Integer quantity = payload.get("quantity") != null ? payload.get("quantity").intValue() : null;

        if (productId == null || quantity == null || quantity <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CART_ADD_REQUEST));
        }

        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.addItemToCart(userId, productId, quantity)) // Service returns Mono<CartItem>
                // Cast to raw type StandardResponseEntity
                .map(updatedCartItem -> (StandardResponseEntity) StandardResponseEntity.ok(updatedCartItem, ApiResponseMessages.CART_ITEM_ADDED_SUCCESS))
                // Error handling based on potential service exceptions
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .onErrorResume(InsufficientStockException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
                .onErrorResume(IllegalStateException.class, e -> // Catches authentication/security context errors
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage()))) // Add an unauthorized helper to StandardResponseEntity
                .onErrorResume(Exception.class, e -> // General fallback error
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_ADDING_CART_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to retrieve the authenticated user's entire cart, enriched with details.
     * Requires the user to be authenticated.
     */
    @GetMapping
    public Mono<StandardResponseEntity> getUserCart() {
        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.getUserCartDetails(userId)) // Service returns Mono<CartDto>
                // Cast to raw type StandardResponseEntity
                .map(userCartDetails -> (StandardResponseEntity) StandardResponseEntity.ok(userCartDetails, ApiResponseMessages.CART_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> // Cart not found for user (empty or nonexistent)
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.CART_NOT_FOUND_FOR_USER)))
                .onErrorResume(IllegalStateException.class, e -> // Catches authentication/security context errors
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> // General fallback error
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_CART + ": " + e.getMessage())));
    }

    /**
     * Endpoint to update the quantity of a product in the authenticated user's cart.
     * If the new quantity is 0, the item will be removed.
     * Requires the user to be authenticated.
     */
    @PutMapping("/update")
    public Mono<StandardResponseEntity> updateCartItem(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        Integer quantity = payload.get("quantity") != null ? payload.get("quantity").intValue() : null;

        if (productId == null || quantity == null || quantity < 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CART_UPDATE_REQUEST));
        }

        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.updateCartItemQuantity(userId, productId, quantity)) // Service returns Mono<CartItem> (or Mono.empty() if removed)
                .map(updatedCartItem -> (StandardResponseEntity) StandardResponseEntity.ok(updatedCartItem, ApiResponseMessages.CART_ITEM_UPDATED_SUCCESS))
                .switchIfEmpty(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.CART_ITEM_REMOVED_SUCCESS))) // Handle case where quantity is 0 and item is removed, service returns Mono.empty()
                // Error handling based on potential service exceptions
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .onErrorResume(InsufficientStockException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
                .onErrorResume(IllegalStateException.class, e -> // Catches authentication/security context errors
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> // General fallback error
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_CART_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to remove a product from the authenticated user's cart.
     * Requires the user to be authenticated.
     */
    @DeleteMapping("/remove")
    public Mono<StandardResponseEntity> removeProductFromCart(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");

        if (productId == null) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CART_REMOVE_REQUEST));
        }

        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.removeCartItem(userId, productId)) // Service returns Mono<Void>
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.CART_ITEM_REMOVED_SUCCESS))) // After completion, return success
                // Error handling based on potential service exceptions
                .onErrorResume(ResourceNotFoundException.class, e -> // Cart item/product not found
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND_IN_CART + productId)))
                .onErrorResume(IllegalStateException.class, e -> // Catches authentication/security context errors
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> // General fallback error
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_REMOVING_CART_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to clear the authenticated user's entire cart.
     * Requires the user to be authenticated.
     */
    @DeleteMapping("/clear")
    public Mono<StandardResponseEntity> clearCart() {
        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.clearCart(userId)) // Service returns Mono<Void>
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.CART_CLEARED_SUCCESS)))
                .onErrorResume(IllegalStateException.class, e -> // Catches authentication/security context errors
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> // General fallback error
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CLEARING_CART + ": " + e.getMessage())));
    }
}