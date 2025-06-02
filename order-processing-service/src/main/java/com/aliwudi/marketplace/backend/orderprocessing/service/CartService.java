package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.dto.CartDto;
import com.aliwudi.marketplace.backend.common.dto.CartItemDto;
import com.aliwudi.marketplace.backend.common.dto.ProductDto;
import com.aliwudi.marketplace.backend.common.dto.UserDto;
import com.aliwudi.marketplace.backend.common.intersevice.ProductIntegrationService;
import com.aliwudi.marketplace.backend.common.intersevice.UserIntegrationService;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.orderprocessing.model.Cart;
import com.aliwudi.marketplace.backend.orderprocessing.model.CartItem;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import org.springframework.data.domain.Pageable; // For pagination

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductIntegrationService productIntegrationService;
    private final UserIntegrationService userIntegrationService;

    @Autowired
    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductIntegrationService productIntegrationService,
                       UserIntegrationService userIntegrationService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productIntegrationService = productIntegrationService;
        this.userIntegrationService = userIntegrationService;
    }

    /**
     * Finds the cart for the given user ID, or creates a new one if it doesn't
     * exist.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the user's Cart.
     */
    public Mono<Cart> getOrCreateCartForUser(Long userId) {
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
     * @throws ResourceNotFoundException if the product is not found.
     * @throws InsufficientStockException if there's not enough stock.
     */
    public Mono<CartItem> addItemToCart(Long userId, Long productId, Integer quantity) {
        if (quantity <= 0) {
            return Mono.error(new IllegalArgumentException("Quantity must be greater than zero."));
        }

        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> productIntegrationService.getProductDtoById(productId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with id: " + productId)))
                        .flatMap(productDto -> {
                            // Check for sufficient stock before adding/updating
                            if (productDto.getStock() == null || productDto.getStock() < quantity) {
                                return Mono.error(new InsufficientStockException("Insufficient stock for product " + productId + ". Available: " + productDto.getStock()));
                            }

                            return cartItemRepository.findByCartIdAndProductId(userCart.getId(), productId)
                                    .flatMap(existingCartItem -> {
                                        // Item exists, update quantity
                                        int newTotalQuantity = existingCartItem.getQuantity() + quantity;
                                        if (productDto.getStock() != null && newTotalQuantity > productDto.getStock()) {
                                            return Mono.error(new InsufficientStockException("Adding " + quantity + " units would exceed available stock for product " + productId + ". Available: " + productDto.getStock()));
                                        }
                                        existingCartItem.setQuantity(newTotalQuantity);
                                        return cartItemRepository.save(existingCartItem);
                                    })
                                    .switchIfEmpty(Mono.defer(() -> {
                                        // Item does not exist, create new
                                        CartItem newCartItem = new CartItem(userCart.getId(), productId, quantity);
                                        return cartItemRepository.save(newCartItem);
                                    }));
                        })
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
                    System.err.println("Error fetching user " + userId + " from User Service: " + e.getMessage());
                    return Mono.error(new RuntimeException("Could not fetch user details for ID: " + userId, e));
                });


        // Step 3: Combine cart and user details, and process cart items
        return Mono.zip(cartMono, userDtoMono)
                .flatMap(tuple -> {
                    Cart userCart = tuple.getT1();
                    UserDto userDto = tuple.getT2();

                    return cartItemRepository.findByCartId(userCart.getId(), Pageable.unpaged())
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
                                        CartItemDto cartItemDto = new CartItemDto();
                                        cartItemDto.setId(item.getId());
                                        cartItemDto.setQuantity(item.getQuantity());
                                        cartItemDto.setProduct(null);
                                        return Mono.just(cartItemDto);
                                    })
                                    .onErrorResume(RuntimeException.class, e -> {
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
                                        .filter(itemDto -> itemDto.getProduct() != null && itemDto.getProduct().getPrice() != null)
                                        .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                                CartDto cartDto = new CartDto();
                                cartDto.setId(userCart.getId());
                                cartDto.setItems(cartItemDtos);
                                cartDto.setUser(userDto);
                                cartDto.setTotalAmount(totalAmount);
                                return cartDto;
                            });
                });
    }

    /**
     * Updates the quantity of a specific product in the specified user's cart.
     * If the new quantity is 0, the item will be removed.
     *
     * @param userId      The ID of the user whose cart to modify.
     * @param productId   The ID of the product whose quantity to update.
     * @param newQuantity The new quantity for the product.
     * @return A Mono emitting the updated CartItem, or Mono.empty() if the item was removed.
     * @throws IllegalArgumentException if newQuantity is negative.
     * @throws ResourceNotFoundException if the product or cart item is not found.
     * @throws InsufficientStockException if there's not enough stock.
     */
    public Mono<CartItem> updateCartItemQuantity(Long userId, Long productId, Integer newQuantity) {
        if (newQuantity < 0) {
            return Mono.error(new IllegalArgumentException("Quantity cannot be negative."));
        }

        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> productIntegrationService.getProductDtoById(productId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product not found with id: " + productId)))
                        .flatMap(productDto -> {
                            if (newQuantity == 0) {
                                // If new quantity is 0, remove the item
                                return cartItemRepository.deleteByCartIdAndProductId(userCart.getId(), productId)
                                        .then(Mono.empty()); // Return empty Mono to indicate removal
                            } else {
                                // Check for sufficient stock before updating
                                if (productDto.getStock() == null || newQuantity > productDto.getStock()) {
                                    return Mono.error(new InsufficientStockException("Cannot set quantity to " + newQuantity + " for product " + productId + ". Available: " + productDto.getStock()));
                                }

                                return cartItemRepository.findByCartIdAndProductId(userCart.getId(), productId)
                                        .flatMap(existingCartItem -> {
                                            // Item exists, update quantity
                                            existingCartItem.setQuantity(newQuantity);
                                            return cartItemRepository.save(existingCartItem);
                                        })
                                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart item not found for product ID: " + productId + " in cart for user ID: " + userId)));
                            }
                        })
                );
    }

    /**
     * Removes a specific product from the specified user's cart.
     *
     * @param userId    The ID of the user whose cart to modify.
     * @param productId The ID of the product to remove.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the cart item is not found.
     */
    public Mono<Void> removeCartItem(Long userId, Long productId) {
        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> cartItemRepository.findByCartIdAndProductId(userCart.getId(), productId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product with ID " + productId + " not found in cart for user ID " + userId)))
                        .flatMap(cartItem -> cartItemRepository.deleteById(cartItem.getId()))
                );
    }

    /**
     * Clears the entire cart for a given user.
     *
     * @param userId The ID of the user whose cart to clear.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the cart is not found.
     */
    public Mono<Void> clearCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart not found for user ID: " + userId)))
                .flatMap(userCart -> cartItemRepository.deleteByCartId(userCart.getId()))
                .then();
    }

    // --- CartItem Repository Implementations (from previous update) ---

    /**
     * Retrieves all cart items with pagination.
     * @param pageable Pagination information.
     * @return A Flux of CartItem.
     */
    public Flux<CartItem> findAllCartItems(Pageable pageable) {
        return cartItemRepository.findAllBy(pageable);
    }

    /**
     * Retrieves all cart items for a specific cart with pagination.
     * @param cartId The ID of the cart.
     * @param pageable Pagination information.
     * @return A Flux of CartItem.
     */
    public Flux<CartItem> findCartItemsByCartId(Long cartId, Pageable pageable) {
        return cartItemRepository.findByCartId(cartId, pageable);
    }

    /**
     * Retrieves all cart items containing a specific product with pagination.
     * @param productId The ID of the product.
     * @param pageable Pagination information.
     * @return A Flux of CartItem.
     */
    public Flux<CartItem> findCartItemsByProductId(Long productId, Pageable pageable) {
        return cartItemRepository.findByProductId(productId, pageable);
    }

    /**
     * Finds a specific cart item by cart ID and product ID.
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting the CartItem.
     */
    public Mono<CartItem> findSpecificCartItem(Long cartId, Long productId) {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId);
    }

    /**
     * Counts all cart items.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countAllCartItems() {
        return cartItemRepository.count();
    }

    /**
     * Counts all cart items for a specific cart.
     * @param cartId The ID of the cart.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countCartItemsByCartId(Long cartId) {
        return cartItemRepository.countByCartId(cartId);
    }

    /**
     * Counts all cart items for a specific product.
     * @param productId The ID of the product.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countCartItemsByProductId(Long productId) {
        return cartItemRepository.countByProductId(productId);
    }

    /**
     * Checks if a specific product exists in a specific cart.
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> checkProductExistsInCart(Long cartId, Long productId) {
        return cartItemRepository.existsByCartIdAndProductId(cartId, productId);
    }

    /**
     * Deletes all cart items for a given cart ID.
     * @param cartId The ID of the cart.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteAllCartItemsByCartId(Long cartId) {
        return cartItemRepository.deleteByCartId(cartId);
    }

    /**
     * Deletes a cart item for a given userId and ProductId.
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteCartItemByUserIdAndProductId(Long userId, Long productId) {
        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> cartItemRepository.deleteByCartIdAndProductId(userCart.getId(), productId));
    }

    /**
     * Directly updates the quantity of a cart item using cart ID and product ID.
     * @param quantity The new quantity.
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting the number of rows updated (Integer).
     */
    public Mono<Integer> directUpdateCartItemQuantity(Long quantity, Long cartId, Long productId) {
        return cartItemRepository.updateQuantityByCartIdAndProductId(quantity, cartId, productId);
    }

    // --- NEW: Cart Repository Implementations ---

    /**
     * Retrieves all carts with pagination.
     * @param pageable Pagination information.
     * @return A Flux of Cart.
     */
    public Flux<Cart> findAllCarts(Pageable pageable) {
        return cartRepository.findAllBy(pageable);
    }

    /**
     * Finds a cart by its associated user ID.
     * @param userId The ID of the user.
     * @return A Mono emitting the Cart, or Mono.empty() if not found.
     */
    public Mono<Cart> findCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId);
    }

    /**
     * Counts all carts.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countAllCarts() {
        return cartRepository.count();
    }

    /**
     * Checks if a cart exists for a given user ID.
     * @param userId The ID of the user.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> checkCartExistsByUserId(Long userId) {
        return cartRepository.existsByUserId(userId);
    }

    /**
     * Deletes a cart by user ID. This will also trigger deletion of associated cart items
     * if cascading delete is configured in the database or if handled explicitly here.
     * For now, it will explicitly delete cart items first.
     *
     * @param userId The ID of the user whose cart to delete.
     * @return A Mono<Void> indicating completion.
     */
    public Mono<Void> deleteCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart not found for user ID: " + userId + " to delete.")))
                .flatMap(cart -> cartItemRepository.deleteByCartId(cart.getId()) // Delete associated cart items first
                        .then(cartRepository.deleteById(cart.getId())) // Then delete the cart itself
                );
    }
}
