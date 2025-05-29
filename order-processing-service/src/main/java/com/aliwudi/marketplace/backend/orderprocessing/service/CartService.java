// CartService.java
package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.dto.CartDto;
import com.aliwudi.marketplace.backend.common.dto.CartItemDto;
import com.aliwudi.marketplace.backend.common.dto.ProductDto;
import com.aliwudi.marketplace.backend.common.dto.UserDto;
import com.aliwudi.marketplace.backend.common.intersevice.ProductIntegrationService;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartRepository;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import Transactional annotation

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductIntegrationService productIntegrationService;

    @Autowired
    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository, ProductIntegrationService productIntegrationService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productIntegrationService = productIntegrationService;
    }

    /**
     * Finds the cart for the given user ID, or creates a new one if it doesn't
     * exist.
     *
     * @param userId The ID of the user.
     * @return The user's Cart.
     */
    @Transactional
    public Cart getOrCreateCartForUser(Long userId) {
        // ... (existing logic) ...
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart(userId);
                    return cartRepository.save(newCart);
                });
    }

    /**
     * Adds a product to the specified user's cart or updates its quantity if
     * already present.
     *
     * @param userId The ID of the user whose cart to modify.
     * @param productId The ID of the product to add.
     * @param quantity The quantity to add/update.
     * @return The updated CartItem.
     * @throws ResourceNotFoundException if the product is not found.
     * @throws IllegalArgumentException if quantity is invalid.
     */
    @Transactional
    public CartItem addItemToCart(Long userId, Long productId, Integer quantity) { // Added userId parameter
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Cart userCart = getOrCreateCartForUser(userId); // Use the userId parameter

        Mono<ProductDto> prdMono = productIntegrationService.getProductByIdWebClient(productId);
        ProductDto productDto = prdMono.block();
        if (prdMono == null || productDto == null) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        Optional<CartItem> existingCartItemOptional = cartItemRepository.findByCartAndProductId(userCart, productId);

        CartItem cartItem;
        if (existingCartItemOptional.isPresent()) {
            cartItem = existingCartItemOptional.get();
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
        } else {
            cartItem = new CartItem(userCart, productId, quantity);
            userCart.getItems().add(cartItem);
        }

        cartRepository.save(userCart);
        return cartItem;
    }

    /**
     * Retrieves the specified user's cart with all its items, enriched with
     * User and Product details. Returns a DTO (CartDto) for better API
     * representation.
     *
     * @param userId The ID of the user whose cart to retrieve.
     * @return The CartDto object of the user.
     * @throws ResourceNotFoundException if the user or their cart is not found.
     */
    @Transactional(readOnly = true)
    public CartDto getUserCartDetails(Long userId) { // Added userId parameter
        Cart userCart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user ID: " + userId));

        UserDto userDto = null;

        Set<CartItemDto> cartItemDtos = userCart.getItems().stream()
                .map(item -> {
                    CartItemDto cartItemDto = new CartItemDto();
                    Mono<ProductDto> prdMono
                            = productIntegrationService
                                    .getProductByIdWebClient(item.getProductId());
                    ProductDto productDto = prdMono.block();
                    if (productDto == null) {
                        System.err.println("Product details not found for productId: " + item.getProductId() + " in Product Catalog Service.");
                        return cartItemDto; //COME BACK - SHOULD RETURN ERROR HERE
                    }

                    cartItemDto.setId(item.getId());
                    cartItemDto.setProduct(productDto);
                    cartItemDto.setQuantity(item.getQuantity());

                    return cartItemDto;
                })
                .collect(Collectors.toSet());

        BigDecimal totalAmount = cartItemDtos.stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CartDto cartDto = new CartDto();
        cartDto.setId(userId);
        cartDto.setItems(cartItemDtos);
        cartDto.setUser(userDto);
        cartDto.setTotalAmount(totalAmount);

        return cartDto;
    }

    /**
     * Updates the quantity of a specific product in the specified user's cart.
     *
     * @param userId The ID of the user whose cart to modify.
     * @param productId The ID of the product whose quantity to update.
     * @param newQuantity The new quantity for the product.
     * @return The updated CartItem, or null if the item was removed.
     * @throws ResourceNotFoundException if the product or cart item is not
     * found.
     * @throws IllegalArgumentException if newQuantity is negative.
     */
    @Transactional
    public CartItem updateCartItemQuantity(Long userId, Long productId, Integer newQuantity) { // Added userId parameter
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }

        Cart userCart = getOrCreateCartForUser(userId); // Use the userId parameter

        Mono<ProductDto> prdMono 
                = productIntegrationService
                        .getProductByIdWebClient(productId);
        ProductDto productDto = prdMono.block();
        if (productDto == null) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        CartItem cartItem = cartItemRepository.findByCartAndProductId(userCart, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + productId + " not found in cart."));

        if (newQuantity == 0) {
            userCart.getItems().remove(cartItem);
            cartItemRepository.delete(cartItem);
            cartRepository.save(userCart);
            return null;
        } else {
            cartItem.setQuantity(newQuantity);
            return cartItem;
        }
    }

    /**
     * Removes a specific product from the specified user's cart.
     *
     * @param userId The ID of the user whose cart to modify.
     * @param productId The ID of the product to remove.
     * @throws ResourceNotFoundException if the product or cart item is not
     * found.
     */
    @Transactional
    public void removeCartItem(Long userId, Long productId) { // Added userId parameter
        Cart userCart = getOrCreateCartForUser(userId); // Use the userId parameter

        ProductDto productDto = productCatalogServiceClient.getProductById(productId);
        if (productDto == null) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        CartItem cartItem = cartItemRepository.findByCartAndProductId(userCart, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + productId + " not found in cart."));

        userCart.getItems().remove(cartItem);
        cartItemRepository.delete(cartItem);
        cartRepository.save(userCart);
    }

    // You will add more methods here later:
    // - clearCart()
}
