package com.aliwudi.marketplace.backend.orderprocessing.service;

import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.intersevice.ProductIntegrationService;
import com.aliwudi.marketplace.backend.common.intersevice.UserIntegrationService;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.InsufficientStockException;
import com.aliwudi.marketplace.backend.common.model.Cart;
import com.aliwudi.marketplace.backend.common.model.CartItem;
import com.aliwudi.marketplace.backend.common.model.Product;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartItemRepository;
import com.aliwudi.marketplace.backend.orderprocessing.repository.CartRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor; // Use Lombok's RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j; // For logging
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable; // For pagination
import org.springframework.transaction.annotation.Transactional; // For transaction management

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // Generates constructor for final fields, replacing @Autowired
@Slf4j // Enables Lombok's logging
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductIntegrationService productIntegrationService;
    private final UserIntegrationService userIntegrationService;

    // IMPORTANT: These prepareDto methods are moved from the controller
    // and kept *exactly* as provided by you. They are now private helper methods
    // within the service to enrich the entities before they are returned.
    /**
     * Helper method to map Cart entity to Cart DTO for public exposure.
     * This method enriches the Cart object with User and CartItem details
     * by making integration calls.
     */
    private Mono<Cart> prepareDto(Cart cart) {
        if (cart == null) {
            return Mono.empty();
        }

        // We use Flux.fromIterable and Mono.zip to handle multiple potential async calls
        // from the two if-conditions.
        List<Mono<?>> enrichmentMonos = new java.util.ArrayList<>();

        // If user is not already set in the cart, fetch it
        if (cart.getUser() == null && cart.getUserId() != null) {
            enrichmentMonos.add(userIntegrationService.getUserById(cart.getUserId())
                    .doOnNext(cart::setUser) // Set user on the cart if found
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch user {}: {}", cart.getUserId(), e.getMessage());
                        // If user fetching fails, provide a placeholder or keep null,
                        // depending on how you want to handle missing user data in the response.
                        // For now, we'll log and return Mono.empty() to not block,
                        // meaning cart.setUser() will not be called for this part.
                        return Mono.empty();
                    })
            );
        }

        // If items are not already set in the cart (or are empty), fetch them
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            enrichmentMonos.add(cartItemRepository.findByCartId(cart.getId())
                    .flatMap(this::prepareDto) // Recursively enrich each cart item
                    .collectList()
                    .doOnNext(cart::setItems) // Set items on the cart
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch cart items for cart {}: {}", cart.getId(), e.getMessage());
                        return Mono.empty();
                    })
            );
        }

        // If there are no enrichment calls needed, return the cart directly
        if (enrichmentMonos.isEmpty()) {
            return Mono.just(cart);
        }

        // Zip all enrichment monos and then return the cart.
        // The doOnNext calls above will have already populated the cart.
        return Mono.zip(enrichmentMonos, (Object[] results) -> cart)
                .defaultIfEmpty(cart); // Ensure cart is returned even if zip is empty or throws error
    }

    /**
     * Helper method to map CartItem entity to CartItem DTO for public exposure.
     * This method enriches the CartItem object with Product details
     * by making integration calls.
     */
    private Mono<CartItem> prepareDto(CartItem cartItem) {
        if (cartItem == null) {
            return Mono.empty();
        }

        // If product is not already set in the cart item, fetch it
        if (cartItem.getProduct() == null && cartItem.getProductId() != null) {
            return productIntegrationService.getProductById(cartItem.getProductId())
                    .doOnNext(cartItem::setProduct) // Set product on the cart item if found
                    .map(product -> cartItem) // Return the modified cartItem
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch product {}: {}", cartItem.getProductId(), e.getMessage());
                        // If product fetching fails, set product to null or a placeholder
                        cartItem.setProduct(null); // Set to null as per original prepareDto behavior
                        return Mono.just(cartItem); // Return the modified cartItem
                    });
        }
        return Mono.just(cartItem); // Return the original cartItem if no product fetching needed
    }

    // Helper method to calculate total amount, renamed for clarity
    BigDecimal calculateTotalCartItemsAmount(List<CartItem> cartItemList) {
        return cartItemList.stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getPrice() != null)
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                    Cart newCart = Cart.builder()
                            .userId(userId)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now()) // Ensure updatedAt is also set for new cart
                            .build();
                    log.info("Created new cart for user: {}", userId);
                    return cartRepository.save(newCart);
                }));
    }

    /**
     * Adds a product to the specified user's cart or updates its quantity if
     * already present. This method ensures product and user details are fetched
     * and stock is checked.
     * It returns the updated/created Cart, including enriched details.
     *
     * @param userId The ID of the user whose cart to modify.
     * @param productId The ID of the product to add.
     * @param quantity The quantity to add/update.
     * @return A Mono emitting the updated/created Cart.
     * @throws IllegalArgumentException if quantity is invalid.
     * @throws ResourceNotFoundException if the product or user is not found.
     * @throws InsufficientStockException if there's not enough stock.
     */
    @Transactional // Ensures atomicity for database operations
    public Mono<Cart> addItemToCart(Long userId, Long productId, Integer quantity) {
        if (quantity <= 0) {
            return Mono.error(new IllegalArgumentException("Quantity must be greater than zero."));
        }

        // Fetch user and product details concurrently, and get/create cart
        Mono<User> userMono = userIntegrationService.getUserById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User", "ID", userId)))
                .onErrorResume(e -> {
                    log.error("Error fetching user {} from User Service: {}", userId, e.getMessage());
                    return Mono.error(new RuntimeException("Could not fetch user details for ID: " + userId, e));
                });

        Mono<Product> productMono = productIntegrationService.getProductById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product", "ID", productId)));

        return Mono.zip(userMono, productMono, getOrCreateCartForUser(userId))
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    Product product = tuple.getT2();
                    Cart userCart = tuple.getT3();

                    // Check initial stock before any cart item operations
                    if (product.getStockQuantity() == null || product.getStockQuantity() < quantity) {
                        return Mono.error(new InsufficientStockException("Insufficient stock for product " + productId + ". Available: " + product.getStockQuantity()));
                    }

                    return cartItemRepository.findByCartIdAndProductId(userCart.getId(), productId)
                            .flatMap(existingCartItem -> {
                                // Item exists, update quantity
                                int newTotalQuantity = existingCartItem.getQuantity() + quantity;
                                if (product.getStockQuantity() != null && newTotalQuantity > product.getStockQuantity()) {
                                    return Mono.error(new InsufficientStockException("Adding " + quantity + " units would exceed available stock for product " + productId + ". Available: " + product.getStockQuantity()));
                                }
                                existingCartItem.setProduct(product); // Set product on CartItem for total calculation consistency
                                existingCartItem.setQuantity(newTotalQuantity);
                                existingCartItem.setUpdatedAt(LocalDateTime.now());
                                return cartItemRepository.save(existingCartItem)
                                        .doOnNext(item -> log.info("Updated cart item quantity for product {} in cart {}", productId, userCart.getId()));
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // Item does not exist, create new
                                CartItem newCartItem = CartItem.builder()
                                        .cartId(userCart.getId())
                                        .productId(productId)
                                        .product(product) // Set product on new CartItem for total calculation consistency
                                        .quantity(quantity)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now()) // Set updatedAt for new item
                                        .build();
                                return cartItemRepository.save(newCartItem)
                                        .doOnNext(item -> log.info("Added new cart item for product {} to cart {}", productId, userCart.getId()));
                            }))
                            // After saving/updating an item, get the full enriched cart for response
                            .flatMap(savedCartItem -> getUserCart(userId)); // No need for final prepareDto here as getUserCart already calls it
                });
    }

    /**
     * Retrieves the specified user's cart with all its items, enriched with
     * User and Product details. Returns a Cart object with all details populated.
     *
     * @param userId The ID of the user whose cart to retrieve.
     * @return A Mono emitting the Cart object of the user.
     * @throws ResourceNotFoundException if the cart is not found.
     */
    public Mono<Cart> getUserCart(Long userId) {
        // Step 1: Find the user's cart
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart", "user ID", userId)))
                .flatMap(cart -> {
                    // Set the user ID in the cart directly for prepareDto
                    cart.setUserId(userId);
                    return this.prepareDto(cart); // Use the prepareDto to enrich the cart
                })
                .doOnNext(cart -> { // Calculate total amount after items are enriched
                    if (cart.getItems() != null) {
                        BigDecimal totalAmount = calculateTotalCartItemsAmount(cart.getItems());
                        cart.setTotalAmount(totalAmount);
                    } else {
                        cart.setTotalAmount(BigDecimal.ZERO);
                    }
                });
    }

    /**
     * Updates the quantity of a specific product in the specified user's cart.
     * If the new quantity is 0, the item will be removed.
     *
     * @param userId The ID of the user whose cart to modify.
     * @param productId The ID of the product whose quantity to update.
     * @param newQuantity The new quantity for the product.
     * @return A Mono emitting the updated Cart (enriched), or Mono.empty() if the item
     * was removed.
     * @throws IllegalArgumentException if newQuantity is negative.
     * @throws ResourceNotFoundException if the product or cart item is not
     * found.
     * @throws InsufficientStockException if there's not enough stock.
     */
    @Transactional
    public Mono<Cart> updateCartItemQuantity(Long userId, Long productId, Integer newQuantity) {
        if (newQuantity < 0) {
            return Mono.error(new IllegalArgumentException("Quantity cannot be negative."));
        }

        Mono<Product> productMono = productIntegrationService.getProductById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product", "ID", productId)));

        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> Mono.zip(Mono.just(userCart), productMono))
                .flatMap(tuple -> {
                    Cart userCart = tuple.getT1();
                    Product product = tuple.getT2();

                    if (newQuantity == 0) {
                        // If new quantity is 0, remove the item
                        return cartItemRepository.deleteByCartIdAndProductId(userCart.getId(), productId)
                                .doOnSuccess(v -> log.info("Removed cart item for product {} from cart {}", productId, userCart.getId()))
                                .then(Mono.empty()); // Indicate removal by returning empty Mono
                    } else {
                        // Check for sufficient stock before updating
                        if (product.getStockQuantity() == null || newQuantity > product.getStockQuantity()) {
                            return Mono.error(new InsufficientStockException("Cannot set quantity to " + newQuantity + " for product " + productId + ". Available: " + product.getStockQuantity()));
                        }

                        return cartItemRepository.findByCartIdAndProductId(userCart.getId(), productId)
                                .flatMap(existingCartItem -> {
                                    // Item exists, update quantity
                                    existingCartItem.setQuantity(newQuantity);
                                    existingCartItem.setProduct(product); // Set product on CartItem for total calculation consistency
                                    existingCartItem.setUpdatedAt(LocalDateTime.now());
                                    return cartItemRepository.save(existingCartItem)
                                            .doOnNext(item -> log.info("Updated cart item quantity to {} for product {} in cart {}", newQuantity, productId, userCart.getId()));
                                })
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart item", "product ID", productId + " in cart for user ID: " + userId)))
                                .flatMap(savedCartItem -> // After updating an item, get the full enriched cart for response
                                        getUserCart(userId)); // No need for final prepareDto here as getUserCart already calls it
                    }
                });
    }

    /**
     * Removes a specific product from the specified user's cart.
     *
     * @param userId The ID of the user whose cart to modify.
     * @param productId The ID of the product to remove.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the cart item is not found.
     */
    @Transactional
    public Mono<Void> removeCartItem(Long userId, Long productId) {
        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> cartItemRepository.findByCartIdAndProductId(userCart.getId(), productId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart item", "product ID", productId + " not found in cart for user ID: " + userId)))
                        .flatMap(cartItem -> cartItemRepository.deleteById(cartItem.getId()))
                        .doOnSuccess(v -> log.info("Removed cart item for product {} from cart {}", productId, userCart.getId()))
                );
    }

    /**
     * Clears the entire cart for a given user.
     *
     * @param userId The ID of the user whose cart to clear.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the cart is not found.
     */
    @Transactional
    public Mono<Void> clearCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart", "user ID", userId)))
                .flatMap(userCart -> cartItemRepository.deleteByCartId(userCart.getId()))
                .doOnSuccess(v -> log.info("Cleared all items from cart for user {}", userId))
                .then();
    }

    // --- CartItem Repository Implementations (from previous update) ---
    /**
     * Retrieves all cart items with pagination. Returns enriched CartItem.
     *
     * @param pageable Pagination information.
     * @return A Flux of enriched CartItem.
     */
    public Flux<CartItem> findAllCartItems(Pageable pageable) {
        return cartItemRepository.findAllBy(pageable)
                .flatMap(this::prepareDto); // Enrich each cart item
    }

    /**
     * Retrieves all cart items for a specific cart with pagination. Returns enriched CartItem.
     *
     * @param cartId The ID of the cart.
     * @param pageable Pagination information.
     * @return A Flux of enriched CartItem.
     */
    public Flux<CartItem> findCartItemsByCartId(Long cartId, Pageable pageable) {
        return cartItemRepository.findByCartId(cartId, pageable)
                .flatMap(this::prepareDto); // Enrich each cart item
    }

    /**
     * Retrieves all cart items for a specific cart without pagination. Returns enriched CartItem.
     *
     * @param cartId The ID of the cart.
     * @return A Flux of enriched CartItem.
     */
    public Flux<CartItem> findCartItemsByCartId(Long cartId) {
        return cartItemRepository.findByCartId(cartId)
                .flatMap(this::prepareDto); // Enrich each cart item
    }

    /**
     * Retrieves all cart items containing a specific product with pagination. Returns enriched CartItem.
     *
     * @param productId The ID of the product.
     * @param pageable Pagination information.
     * @return A Flux of enriched CartItem.
     */
    public Flux<CartItem> findCartItemsByProductId(Long productId, Pageable pageable) {
        return cartItemRepository.findByProductId(productId, pageable)
                .flatMap(this::prepareDto); // Enrich each cart item
    }

    /**
     * Finds a specific cart item by cart ID and product ID. Returns enriched CartItem.
     *
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting the enriched CartItem.
     * @throws ResourceNotFoundException if the cart item is not found.
     */
    public Mono<CartItem> findSpecificCartItem(Long cartId, Long productId) {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart item", "cart ID and product ID", cartId + " and " + productId)))
                .flatMap(this::prepareDto); // Enrich the cart item
    }

    /**
     * Counts all cart items.
     *
     * @return A Mono emitting the count.
     */
    public Mono<Long> countAllCartItems() {
        return cartItemRepository.count();
    }

    /**
     * Counts all cart items for a specific cart.
     *
     * @param cartId The ID of the cart.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countCartItemsByCartId(Long cartId) {
        return cartItemRepository.countByCartId(cartId);
    }

    /**
     * Counts all cart items for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countCartItemsByProductId(Long productId) {
        return cartItemRepository.countByProductId(productId);
    }

    /**
     * Checks if a specific product exists in a specific cart.
     *
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> checkProductExistsInCart(Long cartId, Long productId) {
        return cartItemRepository.existsByCartIdAndProductId(cartId, productId);
    }

    /**
     * Deletes all cart items for a given cart ID.
     *
     * @param cartId The ID of the cart.
     * @return A Mono<Void> indicating completion.
     */
    @Transactional
    public Mono<Void> deleteAllCartItemsByCartId(Long cartId) {
        return cartItemRepository.deleteByCartId(cartId)
                .doOnSuccess(v -> log.info("Deleted all cart items for cart ID: {}", cartId))
                .then();
    }

    /**
     * Deletes a cart item for a given userId and ProductId.
     *
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono<Void> indicating completion.
     */
    @Transactional
    public Mono<Void> deleteCartItemByUserIdAndProductId(Long userId, Long productId) {
        return getOrCreateCartForUser(userId)
                .flatMap(userCart -> cartItemRepository.deleteByCartIdAndProductId(userCart.getId(), productId))
                .doOnSuccess(v -> log.info("Deleted cart item for user {} and product {}", userId, productId));
    }

    /**
     * Directly updates the quantity of a cart item using cart ID and product
     * ID.
     *
     * @param quantity The new quantity.
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting the number of rows updated (Integer).
     * @throws IllegalArgumentException if quantity is negative.
     * @throws ResourceNotFoundException if the product or cart item is not found.
     * @throws InsufficientStockException if there's not enough stock.
     */
    @Transactional
    public Mono<Integer> directUpdateCartItemQuantity(Integer quantity, Long cartId, Long productId) {
        if (quantity < 0) {
            return Mono.error(new IllegalArgumentException("Quantity cannot be negative."));
        }

        // First, check product stock for the new quantity
        Mono<Product> productMono = productIntegrationService.getProductById(productId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product", "ID", productId)));

        // Then, proceed with updating the cart item
        return Mono.zip(cartItemRepository.findByCartIdAndProductId(cartId, productId).switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart item", "cart ID and product ID", cartId + " and " + productId))), productMono)
                .flatMap(tuple -> {
                    CartItem existingCartItem = tuple.getT1(); // Ensure cart item exists
                    Product product = tuple.getT2();

                    if (product.getStockQuantity() == null || quantity > product.getStockQuantity()) {
                        return Mono.error(new InsufficientStockException("Cannot set quantity to " + quantity + " for product " + productId + ". Available: " + product.getStockQuantity()));
                    }

                    // Proceed with the update if stock is sufficient
                    return cartItemRepository.updateQuantityByCartIdAndProductId(Long.valueOf(quantity), cartId, productId) // Ensure quantity is Long if that's what repository expects
                            .doOnNext(rowsUpdated -> log.info("Directly updated cart item quantity for cart {} product {}. Rows updated: {}", cartId, productId, rowsUpdated));
                });
    }

    // --- NEW: Cart Repository Implementations ---
    /**
     * Retrieves all carts with pagination. Returns enriched Cart.
     *
     * @param pageable Pagination information.
     * @return A Flux of enriched Cart.
     */
    public Flux<Cart> findAllCarts(Pageable pageable) {
        return cartRepository.findAllBy(pageable)
                .flatMap(this::prepareDto); // Enrich each cart
    }

    /**
     * Finds a cart by its associated user ID. Returns enriched Cart.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the enriched Cart, or Mono.empty() if not found.
     * @throws ResourceNotFoundException if the cart is not found.
     */
    public Mono<Cart> findCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart", "user ID", userId)))
                .flatMap(this::prepareDto); // Enrich the cart
    }

    /**
     * Counts all carts.
     *
     * @return A Mono emitting the count.
     */
    public Mono<Long> countAllCarts() {
        return cartRepository.count();
    }

    /**
     * Checks if a cart exists for a given user ID.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> checkCartExistsByUserId(Long userId) {
        return cartRepository.existsByUserId(userId);
    }

    /**
     * Deletes a cart by user ID. This will also delete all cart items associated with this cart.
     *
     * @param userId The ID of the user whose cart to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the cart is not found.
     */
    @Transactional
    public Mono<Void> deleteCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cart not found for user ID: " + userId + " to delete.")))
                .flatMap(cart -> cartItemRepository.deleteByCartId(cart.getId()) // Delete associated cart items first
                        .then(cartRepository.deleteById(cart.getId())) // Then delete the cart itself
                        .doOnSuccess(v -> log.info("Deleted cart {} for user {}", cart.getId(), userId))
                );
    }
}
