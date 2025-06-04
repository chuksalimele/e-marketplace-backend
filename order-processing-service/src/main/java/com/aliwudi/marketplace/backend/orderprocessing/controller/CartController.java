package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.model.Cart;
import com.aliwudi.marketplace.backend.common.model.CartItem;
import com.aliwudi.marketplace.backend.orderprocessing.service.CartService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.orderprocessing.exception.InsufficientStockException;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Map;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * Helper method to map Cart entity to Cart DTO for public exposure.
     */
    private Mono<Cart> prepareDto(Cart cart) {
        if (cart == null) {
            return Mono.empty();
        }
        return cart;
    }

    /**
     * Helper method to map CartItem entity to CartItem DTO for public exposure.
     */
    private Mono<CartItem> prepareDto(CartItem cartItem) {
        if (cartItem == null) {
            return Mono.empty();
        }
        return cartItem;
    }

    /**
     * Helper method to get the authenticated user's ID from the reactive
     * SecurityContextHolder. This ID is propagated by the API Gateway.
     *
     * @return A Mono emitting the authenticated user's ID.
     * @throws IllegalStateException if the user is not authenticated or ID
     * cannot be retrieved.
     */
    private Mono<Long> getAuthenticatedUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        return Mono.error(new IllegalStateException(ApiResponseMessages.UNAUTHENTICATED_USER));
                    }
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
     * Endpoint to add a product to the authenticated user's cart. If the
     * product is already in the cart, its quantity will be updated. Requires
     * the user to be authenticated.
     */
    @PostMapping("/add")
    public Mono<StandardResponseEntity> addProductToCart(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        Integer quantity = payload.get("quantity") != null ? payload.get("quantity").intValue() : null;

        if (productId == null || quantity == null || quantity <= 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CART_ADD_REQUEST));
        }

        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.addItemToCart(userId, productId, quantity))
                .flatMap(this::prepareDto)
                .map(cartItem -> StandardResponseEntity.ok(cartItem, ApiResponseMessages.CART_ITEM_ADDED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .onErrorResume(InsufficientStockException.class, e
                        -> Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
                .onErrorResume(IllegalStateException.class, e -> Mono.just(StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_ADDING_CART_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to retrieve the authenticated user's entire cart, enriched with
     * details. Requires the user to be authenticated.
     */
    @GetMapping
    public Mono<StandardResponseEntity> getUserCart() {
        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.getUserCart(userId))
                .flatMap(this::prepareDto)
                .map(cart -> StandardResponseEntity.ok(cart, ApiResponseMessages.CART_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.CART_NOT_FOUND_FOR_USER)))
                .onErrorResume(IllegalStateException.class, e -> Mono.just(StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_CART + ": " + e.getMessage())));
    }

    /**
     * Endpoint to update the quantity of a product in the authenticated user's
     * cart. If the new quantity is 0, the item will be removed. Requires the
     * user to be authenticated.
     */
    @PutMapping("/update")
    public Mono<StandardResponseEntity> updateCartItem(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        Integer quantity = payload.get("quantity") != null ? payload.get("quantity").intValue() : null;

        if (productId == null || quantity == null || quantity < 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CART_UPDATE_REQUEST));
        }

        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.updateCartItemQuantity(userId, productId, quantity))
                .flatMap(this::prepareDto)
                .map(cartItem -> StandardResponseEntity.ok(cartItem, ApiResponseMessages.CART_ITEM_UPDATED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.ok(null, ApiResponseMessages.CART_ITEM_REMOVED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e
                        -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND + productId)))
                .onErrorResume(InsufficientStockException.class, e
                        -> Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INSUFFICIENT_STOCK + e.getMessage())))
                .onErrorResume(IllegalStateException.class, e -> Mono.just(StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_CART_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to remove a product from the authenticated user's cart. Requires
     * the user to be authenticated.
     */
    @DeleteMapping("/remove")
    public Mono<StandardResponseEntity> removeProductFromCart(@RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");

        if (productId == null) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_CART_REMOVE_REQUEST));
        }

        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.removeCartItem(userId, productId))
                .then(Mono.just(StandardResponseEntity.ok(null, ApiResponseMessages.CART_ITEM_REMOVED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND_IN_CART + productId)))
                .onErrorResume(IllegalStateException.class, e -> Mono.just(StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_REMOVING_CART_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to clear the authenticated user's entire cart. Requires the user
     * to be authenticated.
     */
    @DeleteMapping("/clear")
    public Mono<StandardResponseEntity> clearCart() {
        return getAuthenticatedUserId()
                .flatMap(userId -> cartService.clearCart(userId))
                .then(Mono.just(StandardResponseEntity.ok(null, ApiResponseMessages.CART_CLEARED_SUCCESS)))
                .onErrorResume(IllegalStateException.class, e -> Mono.just(StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CLEARING_CART + ": " + e.getMessage())));
    }

    // --- CartItem Controller Endpoints (from previous update) ---
    /**
     * Endpoint to retrieve all cart items with pagination. Accessible by
     * authenticated users (e.g., for admin purposes or general listing).
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of CartItem.
     */
    @GetMapping("/items/all")
    public Flux<CartItem> getAllCartItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return cartService.findAllCartItems(pageable);
    }

    /**
     * Endpoint to retrieve all cart items for a specific cart ID with
     * pagination.
     *
     * @param cartId The ID of the cart.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of CartItem.
     */
    @GetMapping("/items/byCart/{cartId}")
    public Mono<StandardResponseEntity> getCartItemsByCartId(
            @PathVariable Long cartId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return cartService.findCartItemsByCartId(cartId, pageable)
                .flatMap(this::prepareDto)
                .collectList()
                .map(cartItemList -> StandardResponseEntity.ok(cartItemList, ApiResponseMessages.CART_ITEMS_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.CART_ITEM_NOT_FOUND)))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_CART_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to retrieve all cart items containing a specific product ID with
     * pagination.
     *
     * @param productId The ID of the product.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of CartItem.
     */
    @GetMapping("/items/byProduct/{productId}")
    public Mono<StandardResponseEntity> getCartItemsByProductId(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return cartService.findCartItemsByProductId(productId, pageable)
                .flatMap(this::prepareDto)
                .collectList()
                .map(cartItemList -> StandardResponseEntity.ok(cartItemList, ApiResponseMessages.CART_ITEMS_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.CART_ITEM_NOT_FOUND)))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_CART_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find a specific cart item by cart ID and product ID.
     *
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting the CartItem.
     */
    @GetMapping("/items/find/{cartId}/{productId}")
    public Mono<StandardResponseEntity> findSpecificCartItem(
            @PathVariable Long cartId,
            @PathVariable Long productId) {
        return cartService.findSpecificCartItem(cartId, productId)
                .flatMap(this::prepareDto)
                .map(cartItem -> StandardResponseEntity.ok(cartItem, ApiResponseMessages.CART_ITEM_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.PRODUCT_NOT_FOUND_IN_CART +": "+ productId)))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_CART_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all cart items.
     *
     * @return A Mono emitting the count.
     */
    @GetMapping("/items/count/all")
    public Mono<StandardResponseEntity> countAllCartItems() {
        return cartService.countAllCartItems()
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.CART_ITEM_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_CART_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all cart items for a specific cart.
     *
     * @param cartId The ID of the cart.
     * @return A Mono emitting the count.
     */
    @GetMapping("/items/count/byCart/{cartId}")
    public Mono<StandardResponseEntity> countCartItemsByCartId(@PathVariable Long cartId) {
        return cartService.countCartItemsByCartId(cartId)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.CART_ITEM_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_CART_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all cart items for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the count.
     */
    @GetMapping("/items/count/byProduct/{productId}")
    public Mono<StandardResponseEntity> countCartItemsByProductId(@PathVariable Long productId) {
        return cartService.countCartItemsByProductId(productId)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.CART_ITEM_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_CART_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to check if a specific product exists in a specific cart.
     *
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    @GetMapping("/items/exists/{cartId}/{productId}")
    public Mono<StandardResponseEntity> checkProductExistsInCart(
            @PathVariable Long cartId,
            @PathVariable Long productId) {
        return cartService.checkProductExistsInCart(cartId, productId)
                .map(exists -> StandardResponseEntity.ok(exists, ApiResponseMessages.CART_ITEM_EXISTS_CHECK_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CHECKING_CART_ITEM_EXISTENCE + ": " + e.getMessage())));
    }

    /**
     * Endpoint to delete all cart items for a given cart ID. This is a direct
     * deletion and should be used with caution. The /clear endpoint is
     * preferred for user-facing actions.
     *
     * @param cartId The ID of the cart.
     * @return A Mono<Void> indicating completion.
     */
    @DeleteMapping("/items/admin/deleteByCart/{cartId}")
    public Mono<StandardResponseEntity> deleteAllCartItemsByCartId(@PathVariable Long cartId) {
        return cartService.deleteAllCartItemsByCartId(cartId)
                .then(Mono.just(StandardResponseEntity.ok(null, ApiResponseMessages.CART_ITEMS_DELETED_SUCCESS)))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_CART_ITEMS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to delete a cart item by user ID and product ID. This is an
     * administrative endpoint or for specific use cases where a userId is
     * known.
     *
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono<Void> indicating completion.
     */
    @DeleteMapping("/items/admin/deleteByUserAndProduct/{userId}/{productId}")
    public Mono<StandardResponseEntity> deleteCartItemByUserIdAndProductId(
            @PathVariable Long userId,
            @PathVariable Long productId) {
        return cartService.deleteCartItemByUserIdAndProductId(userId, productId)
                .then(Mono.just(StandardResponseEntity.ok(null, ApiResponseMessages.CART_ITEM_REMOVED_SUCCESS)))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_REMOVING_CART_ITEM + ": " + e.getMessage())));
    }

    /**
     * Endpoint to directly update the quantity of a cart item by cart ID and
     * product ID. This is a direct update, potentially for administrative
     * purposes.
     *
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @param payload A map containing the "quantity" to update.
     * @return A Mono emitting the number of rows updated.
     */
    @PutMapping("/items/admin/directUpdateQuantity/{cartId}/{productId}")
    public Mono<StandardResponseEntity> directUpdateCartItemQuantity(
            @PathVariable Long cartId,
            @PathVariable Long productId,
            @RequestBody Map<String, Long> payload) {
        Long quantity = payload.get("quantity");

        if (quantity == null || quantity < 0) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_QUANTITY_PROVIDED));
        }

        return cartService.directUpdateCartItemQuantity(quantity, cartId, productId)
                .map(rowsUpdatedCount -> StandardResponseEntity.ok(rowsUpdatedCount, ApiResponseMessages.CART_ITEM_UPDATED_SUCCESS + " Rows updated: " + rowsUpdatedCount))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_CART_ITEM + ": " + e.getMessage())));
    }

    // --- NEW: Cart Repository Controller Endpoints ---
    /**
     * Endpoint to retrieve all carts with pagination. Accessible by
     * authenticated users (e.g., for admin purposes).
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Cart.
     */
    @GetMapping("/admin/all")
    public Mono<StandardResponseEntity> getAllCarts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return cartService.findAllCarts(pageable)
                .flatMap(this::prepareDto)
                .collectList()
                .map(cartList -> StandardResponseEntity.ok(cartList, ApiResponseMessages.CART_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.CART_NOT_FOUND)))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_CART + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find a cart by its associated user ID.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the Cart.
     */
    @GetMapping("/byUser/{userId}")
    public Mono<StandardResponseEntity> getCartByUserId(@PathVariable Long userId) {
        return cartService.findCartByUserId(userId)
                .flatMap(this::prepareDto)
                .map(cart -> StandardResponseEntity.ok(cart, ApiResponseMessages.CART_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.CART_NOT_FOUND_FOR_USER + userId)))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_CART + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count all carts.
     *
     * @return A Mono emitting the count.
     */
    @GetMapping("/count")
    public Mono<StandardResponseEntity> countAllCarts() {
        return cartService.countAllCarts()
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.CART_COUNT_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_CARTS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to check if a cart exists for a given user ID.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    @GetMapping("/exists/byUser/{userId}")
    public Mono<StandardResponseEntity> checkCartExistsByUserId(@PathVariable Long userId) {
        return cartService.checkCartExistsByUserId(userId)
                .map(exists -> StandardResponseEntity.ok(exists, ApiResponseMessages.CART_EXISTS_CHECK_SUCCESS))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CHECKING_CART_EXISTENCE + ": " + e.getMessage())));
    }

    /**
     * Endpoint to delete a cart by user ID. This will also delete all cart
     * items associated with this cart.
     *
     * @param userId The ID of the user whose cart to delete.
     * @return A Mono<Void> indicating completion.
     */
    @DeleteMapping("/admin/deleteByUserId/{userId}")
    public Mono<StandardResponseEntity> deleteCartByUserId(@PathVariable Long userId) {
        return cartService.deleteCartByUserId(userId)
                .then(Mono.just(StandardResponseEntity.ok(null, ApiResponseMessages.CART_DELETED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just(StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e -> Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_CART + ": " + e.getMessage())));
    }
}
