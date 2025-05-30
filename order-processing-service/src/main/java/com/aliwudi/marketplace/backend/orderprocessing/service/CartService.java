package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.dto.CartDto;
import com.aliwudi.marketplace.backend.common.dto.CartItemDto;
import com.aliwudi.marketplace.backend.common.dto.ProductDto;
import com.aliwudi.marketplace.backend.common.dto.UserDto; // Assuming UserDto is needed for a more complete cart DTO
import com.aliwudi.marketplace.backend.common.intersevice.ProductIntegrationService;
import com.aliwudi.marketplace.backend.common.intersevice.UserIntegrationService; // NEW: Import UserIntegrationService
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples; // NEW: Import Tuples for Mono.zip

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductIntegrationService productIntegrationService;
    private final UserIntegrationService userIntegrationService; // NEW: Declare UserIntegrationService

    @Autowired
    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductIntegrationService productIntegrationService,
                       UserIntegrationService userIntegrationService) { // NEW: Inject UserIntegrationService
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productIntegrationService = productIntegrationService;
        this.userIntegrationService = userIntegrationService; // NEW: Assign
    }

    /**
     * Finds the cart for the given user ID, or creates a new one if it doesn't
     * exist.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the user's Cart.
     */
    public Mono<Cart> getOrCreateCartForUser(Long userId) {
        // Use flatMap for sequential reactive operations (find then save if empty)
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    Cart newCart = new Cart(userId);
                    return cartRepository.save(newCart);
                }));
    }

    /**
     * Adds a product to the specified user's cart or updates its quantity if
     * already present.
     *
     * @param userId    The ID of the user whose cart to modify.
     * @param productId The ID of the product to add.
     * @param quantity  The quantity to add/update.
     * @return A Mono emitting the updated CartItem.
     * @throws IllegalArgumentException if quantity is invalid.
     */
    public Mono<CartItem> addItemToCart(Long userId, Long productId, Integer quantity) {
        if (quantity <= 0) {
            return Mono.error(new IllegalArgumentException("Quantity must be greater than zero."));
        }

        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> productIntegrationService.getProductDtoById(productId) // Assuming getProductDtoById now
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with id: " + productId)))
                        .flatMap(productDto -> cartItemRepository.findByCartAndProductId(userCart, productId)
                                .flatMap(existingCartItem -> {
                                    // Item exists, update quantity
                                    existingCartItem.setQuantity(existingCartItem.getQuantity() + quantity);
                                    return cartItemRepository.save(existingCartItem);
                                })
                                .switchIfEmpty(Mono.defer(() -> {
                                    // Item does not exist, create new
                                    CartItem newCartItem = new CartItem(userCart, productId, quantity);
                                    return cartItemRepository.save(newCartItem)
                                            .doOnNext(savedItem -> userCart.getItems().add(savedItem))
                                            .flatMap(savedItem -> cartRepository.save(userCart).thenReturn(savedItem)); // Save cart to update relationship if needed
                                }))
                        )
                );
    }

    /**
     * Retrieves the specified user's cart with all its items, enriched with
     * User and Product details. Returns a DTO (CartDto) for better API
     * representation.
     *
     * @param userId The ID of the user whose cart to retrieve.
     * @return A Mono emitting the CartDto object of the user.
     */
    public Mono<CartDto> getUserCartDetails(Long userId) {
        // Step 1: Find the user's cart
        Mono<Cart> cartMono = cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart not found for user ID: " + userId)));

        // Step 2: Fetch user details from User Service
        Mono<UserDto> userDtoMono = userIntegrationService.getUserDtoById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found for ID: " + userId + " from User Service.")))
                .onErrorResume(e -> {
                    // Log the error if user service is down or user not found, but don't fail the whole cart retrieval,
                    // unless a user is absolutely mandatory for the CartDto.
                    // If you want to allow cart details even without user details, return Mono.just(new UserDto(userId, "N/A", "N/A"))
                    // or Mono.empty() and handle userDto being null later.
                    System.err.println("Error fetching user " + userId + " from User Service: " + e.getMessage());
                    return Mono.error(new RuntimeException("Could not fetch user details for ID: " + userId, e));
                });


        // Step 3: Combine cart and user details, and process cart items
        return Mono.zip(cartMono, userDtoMono)
                .flatMap(tuple -> {
                    Cart userCart = tuple.getT1();
                    UserDto userDto = tuple.getT2();

                    return Flux.fromIterable(userCart.getItems())
                            .flatMap(item -> productIntegrationService.getProductDtoById(item.getProductId())
                                    .map(productDto -> {
                                        CartItemDto cartItemDto = new CartItemDto();
                                        cartItemDto.setId(item.getId());
                                        cartItemDto.setProduct(productDto);
                                        cartItemDto.setQuantity(item.getQuantity());
                                        return cartItemDto;
                                    })
                                    .onErrorResume(ResourceNotFoundException.class, e -> {
                                        System.err.println("Product details not found for productId: " + item.getProductId() + " in Product Catalog Service. Error: " + e.getMessage());
                                        // Return a CartItemDto with null product to indicate it couldn't be fetched
                                        CartItemDto cartItemDto = new CartItemDto();
                                        cartItemDto.setId(item.getId());
                                        cartItemDto.setQuantity(item.getQuantity()); // Keep quantity
                                        cartItemDto.setProduct(null); // Explicitly set product to null
                                        return Mono.just(cartItemDto); // Emit the DTO even if product fetch failed
                                    })
                                    .onErrorResume(RuntimeException.class, e -> { // Catch other potential errors from product service
                                        System.err.println("Error fetching product " + item.getProductId() + " from Product Catalog Service: " + e.getMessage());
                                        CartItemDto cartItemDto = new CartItemDto();
                                        cartItemDto.setId(item.getId());
                                        cartItemDto.setQuantity(item.getQuantity());
                                        cartItemDto.setProduct(null);
                                        return Mono.just(cartItemDto);
                                    })
                            )
                            .collect(Collectors.toSet())
                            .map(cartItemDtos -> {
                                BigDecimal totalAmount = cartItemDtos.stream()
                                        // Only include items with valid product prices in total calculation
                                        .filter(itemDto -> itemDto.getProduct() != null && itemDto.getProduct().getPrice() != null)
                                        .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                CartDto cartDto = new CartDto();
                                cartDto.setId(userCart.getId());
                                cartDto.setItems(cartItemDtos);
                                cartDto.setUser(userDto); // Assign the fetched UserDto
                                cartDto.setTotalAmount(totalAmount);
                                return cartDto;
                            });
                });
    }


    /**
     * Updates the quantity of a specific product in the specified user's cart.
     *
     * @param userId      The ID of the user whose cart to modify.
     * @param productId   The ID of the product whose quantity to update.
     * @param newQuantity The new quantity for the product.
     * @return A Mono emitting the updated CartItem, or Mono.empty() if the item was removed.
     * @throws IllegalArgumentException if newQuantity is negative.
     */
    public Mono<CartItem> updateCartItemQuantity(Long userId, Long productId, Integer newQuantity) {
        if (newQuantity < 0) {
            return Mono.error(new IllegalArgumentException("Quantity cannot be negative."));
        }

        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> productIntegrationService.getProductDtoById(productId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with id: " + productId)))
                        .flatMap(productDto -> cartItemRepository.findByCartAndProductId(userCart, productId)
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product with id " + productId + " not found in cart.")))
                                .flatMap(cartItem -> {
                                    if (newQuantity == 0) {
                                        // Remove item
                                        return cartItemRepository.delete(cartItem)
                                                .then(Mono.defer(() -> {
                                                    userCart.getItems().remove(cartItem); // Update in-memory cart object
                                                    return cartRepository.save(userCart); // Persist the cart changes
                                                }))
                                                .then(Mono.empty()); // Return empty Mono after deletion
                                    } else {
                                        // Update quantity
                                        cartItem.setQuantity(newQuantity);
                                        return cartItemRepository.save(cartItem);
                                    }
                                })
                        )
                );
    }

    /**
     * Removes a specific product from the specified user's cart.
     *
     * @param userId    The ID of the user whose cart to modify.
     * @param productId The ID of the product to remove.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> removeCartItem(Long userId, Long productId) {
        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> productIntegrationService.getProductDtoById(productId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with id: " + productId)))
                        .flatMap(productDto -> cartItemRepository.findByCartAndProductId(userCart, productId)
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product with id " + productId + " not found in cart.")))
                                .flatMap(cartItem -> cartItemRepository.delete(cartItem)
                                        .then(Mono.defer(() -> {
                                            userCart.getItems().remove(cartItem); // Update in-memory cart object
                                            return cartRepository.save(userCart); // Persist the cart changes
                                        }))
                                )
                        )
                )
                .then(); // Convert to Mono<Void>
    }

    /**
     * Clears all items from the specified user's cart.
     *
     * @param userId The ID of the user whose cart to clear.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> clearCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart not found for user ID: " + userId)))
                .flatMap(cart -> cartItemRepository.deleteByCart(cart) // Assuming you add this method to CartItemRepository
                        .then(Mono.defer(() -> {
                            cart.getItems().clear(); // Clear in-memory cart items
                            return cartRepository.save(cart); // Save the cart to persist the changes
                        }))
                )
                .then(); // Convert to Mono<Void>
    }
}