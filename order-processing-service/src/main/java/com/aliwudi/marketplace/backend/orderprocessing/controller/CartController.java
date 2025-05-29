// CartController.java
package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.dto.CartDto;
import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem;
import com.aliwudi.marketplace.backend.orderprocessing.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication; // Import Authentication

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    @Autowired
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Helper method to get the authenticated user's ID from the SecurityContextHolder.
     * This ID is propagated by the API Gateway.
     * @return The authenticated user's ID.
     * @throws IllegalStateException if the user is not authenticated or ID cannot be retrieved.
     */
    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated.");
        }
        // Assuming the principal is the Long userId set by CustomUserIdHeaderFilter
        // You might need to cast or convert based on what you put into the principal.
        if (authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        } else if (authentication.getPrincipal() instanceof String) {
            // If you put the userId as a String in the principal (e.g., from JWT 'sub' claim)
            return Long.parseLong((String) authentication.getPrincipal());
        }
        throw new IllegalStateException("Authenticated principal is not a valid user ID.");
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
    public ResponseEntity<CartItem> addProductToCart(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        Integer quantity = payload.get("quantity").intValue();

        if (productId == null || quantity == null || quantity <= 0) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Pass the authenticated user ID to the service layer
        Long userId = getAuthenticatedUserId();
        CartItem updatedCartItem = cartService.addItemToCart(userId, productId, quantity); // Modified service call
        return new ResponseEntity<>(updatedCartItem, HttpStatus.OK);
    }

    /**
     * Endpoint to retrieve the authenticated user's entire cart, enriched with details.
     * Requires the user to be authenticated.
     */
    @GetMapping
    public ResponseEntity<CartDto> getUserCart() { // Changed return type to DTO
        // Pass the authenticated user ID to the service layer
        Long userId = getAuthenticatedUserId();
        CartDto userCartDetails = cartService.getUserCartDetails(userId); // Modified service call
        return new ResponseEntity<>(userCartDetails, HttpStatus.OK);
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
    public ResponseEntity<?> updateCartItem(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        Integer quantity = payload.get("quantity").intValue();

        if (productId == null || quantity == null || quantity < 0) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Pass the authenticated user ID to the service layer
        Long userId = getAuthenticatedUserId();
        CartItem updatedCartItem = cartService.updateCartItemQuantity(userId, productId, quantity); // Modified service call

        if (updatedCartItem == null) {
            // Item was removed (quantity set to 0)
            return new ResponseEntity<>("Product removed from cart successfully.", HttpStatus.OK);
        }
        return new ResponseEntity<>(updatedCartItem, HttpStatus.OK);
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
    public ResponseEntity<String> removeProductFromCart(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");

        if (productId == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST.getReasonPhrase(), HttpStatus.BAD_REQUEST);
        }

        // Pass the authenticated user ID to the service layer
        Long userId = getAuthenticatedUserId();
        cartService.removeCartItem(userId, productId); // Modified service call
        return new ResponseEntity<>("Product removed from cart successfully.", HttpStatus.OK);
    }

    // --- Future methods to add: ---
    // @DeleteMapping("/clear") for clearing the entire cart
}