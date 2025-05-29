package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.dto.CartDto;
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem;
import com.aliwudi.marketplace.backend.orderprocessing.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types
import org.springframework.security.core.Authentication; // Keep for mapping principal
import org.springframework.security.core.context.ReactiveSecurityContextHolder; // NEW: For reactive security context

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    @Autowired
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

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
                        return Mono.error(new IllegalStateException("User is not authenticated."));
                    }
                    if (authentication.getPrincipal() instanceof Long) {
                        return Mono.just((Long) authentication.getPrincipal());
                    } else if (authentication.getPrincipal() instanceof String) {
                        try {
                            return Mono.just(Long.parseLong((String) authentication.getPrincipal()));
                        } catch (NumberFormatException e) {
                            return Mono.error(new IllegalStateException("Authenticated principal is not a valid user ID format.", e));
                        }
                    }
                    return Mono.error(new IllegalStateException("Authenticated principal is not a valid user ID."));
                })
                .switchIfEmpty(Mono.error(new IllegalStateException("Security context not found."))); // Handle case where context is empty
    }

    /**
     * Endpoint to add a product to the authenticated user's cart.
     * If the product is already in the cart, its quantity will be updated.
     * Requires the user to be authenticated.
     *
     * Request Body example:
     * {
     * "productId": 1,
     * "quantity": 2
     * }
     */
    @PostMapping("/add")
    public Mono<ResponseEntity<CartItem>> addProductToCart(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        Integer quantity = payload.get("quantity").intValue();

        if (productId == null || quantity == null || quantity <= 0) {
            return Mono.just(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
        }

        // Pass the authenticated user ID to the service layer reactively
        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.addItemToCart(userId, productId, quantity)) // Service returns Mono<CartItem>
                .map(updatedCartItem -> new ResponseEntity<>(updatedCartItem, HttpStatus.OK))
                .onErrorResume(IllegalStateException.class, e -> Mono.just(new ResponseEntity<>(HttpStatus.UNAUTHORIZED))); // Handle authentication errors
    }

    /**
     * Endpoint to retrieve the authenticated user's entire cart, enriched with details.
     * Requires the user to be authenticated.
     */
    @GetMapping
    public Mono<ResponseEntity<CartDto>> getUserCart() { // Changed return type to Mono<ResponseEntity<CartDto>>
        // Pass the authenticated user ID to the service layer reactively
        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.getUserCartDetails(userId)) // Service returns Mono<CartDto>
                .map(userCartDetails -> new ResponseEntity<>(userCartDetails, HttpStatus.OK))
                .onErrorResume(IllegalStateException.class, e -> Mono.just(new ResponseEntity<>(HttpStatus.UNAUTHORIZED))); // Handle authentication errors
    }

    /**
     * Endpoint to update the quantity of a product in the authenticated user's cart.
     * If the new quantity is 0, the item will be removed.
     * Requires the user to be authenticated.
     *
     * Request Body example:
     * {
     * "productId": 1,
     * "quantity": 5
     * }
     */
    @PutMapping("/update")
    public Mono<ResponseEntity<?>> updateCartItem(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        Integer quantity = payload.get("quantity").intValue();

        if (productId == null || quantity == null || quantity < 0) {
            return Mono.just(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
        }

        // Pass the authenticated user ID to the service layer reactively
        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.updateCartItemQuantity(userId, productId, quantity)) // Service returns Mono<CartItem>
                .map(updatedCartItem -> {
                    // Check if the item was effectively removed (service might return empty Mono or null/special value)
                    // Assuming service returns null/empty Mono if item was removed (quantity set to 0)
                    // If your service returns Mono.empty() for removal, adjust this logic.
                    if (updatedCartItem == null) { // Or updatedCartItem.equals(CartItem.REMOVED_PLACEHOLDER)
                        return new ResponseEntity<>("Product removed from cart successfully.", HttpStatus.OK);
                    }
                    return new ResponseEntity<>(updatedCartItem, HttpStatus.OK);
                })
                .switchIfEmpty(Mono.just(new ResponseEntity<>("Product removed from cart successfully.", HttpStatus.OK))) // If service returns Mono.empty() for removal
                .onErrorResume(IllegalStateException.class, e -> Mono.just(new ResponseEntity<>(HttpStatus.UNAUTHORIZED))); // Handle authentication errors
    }

    /**
     * Endpoint to remove a product from the authenticated user's cart.
     * Requires the user to be authenticated.
     *
     * Request Body example:
     * {
     * "productId": 1
     * }
     */
    @DeleteMapping("/remove")
    public Mono<ResponseEntity<String>> removeProductFromCart(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");

        if (productId == null) {
            return Mono.just(new ResponseEntity<>(HttpStatus.BAD_REQUEST.getReasonPhrase(), HttpStatus.BAD_REQUEST));
        }

        // Pass the authenticated user ID to the service layer reactively
        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.removeCartItem(userId, productId)) // Service returns Mono<Void>
                .then(Mono.just(new ResponseEntity<>("Product removed from cart successfully.", HttpStatus.OK))) // After completion, return success
                .onErrorResume(IllegalStateException.class, e -> Mono.just(new ResponseEntity<>(HttpStatus.UNAUTHORIZED))); // Handle authentication errors
    }

    // --- Future methods to add: ---
    // @DeleteMapping("/clear") for clearing the entire cart
    // public Mono<ResponseEntity<String>> clearCart() { ... }
}