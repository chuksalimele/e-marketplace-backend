// CartService.java
package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import Transactional annotation

import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Autowired
    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * Retrieves the currently authenticated user from the SecurityContextHolder.
     * Throws ResourceNotFoundException if user is not found (shouldn't happen if authenticated).
     */
    private User getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    /**
     * Finds the cart for the current user, or creates a new one if it doesn't exist.
     * @return The user's Cart.
     */
    @Transactional // Ensures the operation is atomic and manages persistence context
    public Cart getOrCreateCartForCurrentUser() {
        User currentUser = getCurrentAuthenticatedUser();
        return cartRepository.findByUser(currentUser)
                .orElseGet(() -> {
                    Cart newCart = new Cart(currentUser);
                    return cartRepository.save(newCart); // Save the new cart
                });
    }

    /**
     * Adds a product to the current user's cart or updates its quantity if already present.
     * @param productId The ID of the product to add.
     * @param quantity The quantity to add/update.
     * @return The updated CartItem.
     * @throws ResourceNotFoundException if the product is not found.
     * @throws IllegalArgumentException if quantity is invalid.
     */
    @Transactional
    public CartItem addItemToCart(Long productId, Integer quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Cart userCart = getOrCreateCartForCurrentUser(); // Get or create cart for current user

        //Product product = productRepository.findById(productId)
        //        .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        // Check if the product is already in the cart
        Optional<CartItem> existingCartItemOptional = cartItemRepository.findByCartAndProductId(userCart, productId);

        CartItem cartItem;
        if (existingCartItemOptional.isPresent()) {
            // Product already in cart, update quantity
            cartItem = existingCartItemOptional.get();
            cartItem.setQuantity(cartItem.getQuantity() + quantity); // Add to existing quantity
            // No need to explicitly save cartItem due to @Transactional and managed entity state
        } else {
            // Product not in cart, create new CartItem
            cartItem = new CartItem(userCart, productId, quantity);
            userCart.getItems().add(cartItem); // Add to the cart's collection
            // No need to explicitly save cartItem here, as it will be cascaded when cart is saved/merged
        }

        // Saving the cart will cascade the changes to cart items (due to CascadeType.ALL)
        cartRepository.save(userCart);

        return cartItem;
    }

    /**
     * Retrieves the current user's cart with all its items.
     * @return The Cart object of the current user.
     * @throws ResourceNotFoundException if the user or their cart is not found.
     */
    @Transactional(readOnly = true) // Read-only transaction for fetching data
    public Cart getUserCart() {
        User currentUser = getCurrentAuthenticatedUser();
        // Use findByUser and then load items explicitly if FetchType.LAZY was used,
        // or ensure the initial findByUser fetches items if needed (e.g. by JOIN FETCH)
        // For simplicity, getOrCreateCartForCurrentUser already handles loading the cart.
        Cart userCart = cartRepository.findByUser(currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + currentUser.getUsername()));

        // Ensure cart items are loaded if FetchType.LAZY is used on the items set
        userCart.getItems().size(); // Trigger loading of lazy-loaded items within the transaction

        return userCart;
    }

/**
     * Updates the quantity of a specific product in the current user's cart.
     * If quantity is 0, the item is removed.
     * @param productId The ID of the product whose quantity to update.
     * @param newQuantity The new quantity for the product.
     * @return The updated CartItem, or null if the item was removed.
     * @throws ResourceNotFoundException if the product or cart item is not found.
     * @throws IllegalArgumentException if newQuantity is negative.
     */
    @Transactional
    public CartItem updateCartItemQuantity(Long productId, Integer newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }

        Cart userCart = getOrCreateCartForCurrentUser();

        //Product product = productRepository.findById(productId)
        //        .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        CartItem cartItem = cartItemRepository.findByCartAndProductId(userCart, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + productId + " not found in cart."));

        if (newQuantity == 0) {
            // If new quantity is 0, remove the item
            userCart.getItems().remove(cartItem); // Remove from collection
            cartItemRepository.delete(cartItem); // Explicitly delete from DB
            cartRepository.save(userCart); // Save cart to reflect changes in collection
            return null; // Indicate item was removed
        } else {
            // Update quantity
            cartItem.setQuantity(newQuantity);
            cartItemRepository.save(cartItem); // Save the updated cart item
            // Note: saving cartItem implicitly saves cart if cart is managed.
            // cartRepository.save(userCart); // Optional, but good to keep if you want to ensure cart state is flushed
            return cartItem;
        }
    }

    /**
     * Removes a specific product from the current user's cart.
     * @param productId The ID of the product to remove.
     * @throws ResourceNotFoundException if the product or cart item is not found.
     */
    @Transactional
    public void removeCartItem(Long productId) {
        Cart userCart = getOrCreateCartForCurrentUser();

        //Product product = productRepository.findById(productId)
         //       .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        CartItem cartItem = cartItemRepository.findByCartAndProductId(userCart, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + productId + " not found in cart."));

        userCart.getItems().remove(cartItem); // Remove from the cart's collection
        cartItemRepository.delete(cartItem); // Explicitly delete from database

        // Due to orphanRemoval=true on Cart.items, removing from the collection
        // and saving the cart *should* also delete the item.
        // However, explicit deletion ensures it regardless of the orphanRemoval configuration.
        cartRepository.save(userCart); // Save cart to reflect the change in its collection
    }
        
    // You will add more methods here later:
    // - clearCart()
}