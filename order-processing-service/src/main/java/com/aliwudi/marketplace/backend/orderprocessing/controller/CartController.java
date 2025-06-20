package com.aliwudi.marketplace.backend.orderprocessing.controller;

import com.aliwudi.marketplace.backend.common.model.Cart;
import com.aliwudi.marketplace.backend.common.model.CartItem;
import com.aliwudi.marketplace.backend.orderprocessing.service.CartService;
import com.aliwudi.marketplace.backend.common.util.AuthUtil;
import com.aliwudi.marketplace.backend.orderprocessing.dto.AddItemRequest; // New request DTO
import com.aliwudi.marketplace.backend.orderprocessing.dto.UpdateItemQuantityRequest; // New request DTO

import jakarta.validation.Valid; // For @Valid annotation
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // For logging
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Map; // Used for generic payload for remove/directUpdate, but replaced with DTOs for add/update quantity
import org.springframework.web.server.ServerWebExchange;

// Static import for API path constants
import static com.aliwudi.marketplace.backend.common.constants.ApiConstants.*;

@RestController
@RequestMapping(CART_CONTROLLER_BASE) // MODIFIED
@RequiredArgsConstructor // Generates constructor for final fields
@Slf4j // Enables Lombok's logging
public class CartController {

    private final CartService cartService;
    private final AuthUtil authUtil;
    // Removed direct injection of integration services, as their usage is now confined to CartService.

    /**
     * Endpoint to add a product to the authenticated user's cart. If the
     * product is already in the cart, its quantity will be updated. Requires
     * the user to be authenticated.
     *
     * @param exchange
     * @param request The AddItemRequest containing productId and quantity.
     * @return A Mono emitting the updated Cart.
     */
    @PostMapping(CART_ADD_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK) // Common for updates; can be HttpStatus.CREATED if always new carts
    public Mono<Cart> addProductToCart(
            ServerWebExchange exchange,
            @Valid @RequestBody AddItemRequest request) {
        return authUtil.getAuthenticatedUserId(exchange)
                .flatMap(userId -> cartService.addItemToCart(userId, request.getProductId(), request.getQuantity()));
                // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve the authenticated user's entire cart, enriched with
     * details. Requires the user to be authenticated.
     *
     * @param exchange
     * @return A Mono emitting the Cart.
     */
    @GetMapping(CART_GET_USER_CART) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Cart> getUserCart(ServerWebExchange exchange) {
        return  authUtil.getAuthenticatedUserId(exchange)
                .flatMap(userId -> cartService.getUserCart(userId));
                // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update the quantity of a product in the authenticated user's
     * cart. If the new quantity is 0, the item will be removed. Requires the
     * user to be authenticated.
     *
     * @param exchange
     * @param request The UpdateItemQuantityRequest containing productId and newQuantity.
     * @return A Mono emitting the updated Cart, or Mono.empty() if the item was removed (results in 204).
     */
    @PutMapping(CART_UPDATE_ITEM_QUANTITY) // MODIFIED
    @ResponseStatus(HttpStatus.OK) // 200 OK for update, 204 No Content for removal if Mono.empty() is returned
    public Mono<Cart> updateCartItem(
            ServerWebExchange exchange,
            @Valid @RequestBody UpdateItemQuantityRequest request) {
        return  authUtil.getAuthenticatedUserId(exchange)
                .flatMap(userId -> cartService.updateCartItemQuantity(userId, request.getProductId(), request.getQuantity()))
                .switchIfEmpty(Mono.empty()); // If updateQuantity returns empty, it means item was removed, so return 204
                // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to remove a product from the authenticated user's cart. Requires
     * the user to be authenticated.
     *
     * @param exchange
     * @param payload A map containing the "productId" to remove.
     * @return A Mono<Void> indicating completion (results in 204 No Content).
     */
    @DeleteMapping(CART_REMOVE_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeProductFromCart(
            ServerWebExchange exchange,
            @RequestBody Map<String, Long> payload) {
        Long productId = payload.get("productId");
        if (productId == null) {
            // This exception will be caught by GlobalExceptionHandler
            return Mono.error(new IllegalArgumentException("Product ID is required for removal."));
        }
        return  authUtil.getAuthenticatedUserId(exchange)
                .flatMap(userId -> cartService.removeCartItem(userId, productId));
                // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to clear the authenticated user's entire cart. Requires the user
     * to be authenticated.
     *
     * @param exchange
     * @return A Mono<Void> indicating completion (results in 204 No Content).
     */
    @DeleteMapping(CART_CLEAR) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> clearCart(ServerWebExchange exchange) {
        return  authUtil.getAuthenticatedUserId(exchange)
                .flatMap(userId -> cartService.clearCart(userId));
                // Errors are handled by GlobalExceptionHandler.
    }

    // --- CartItem Controller Endpoints ---

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
    @GetMapping(CART_ITEMS_GET_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<CartItem> getAllCartItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return cartService.findAllCartItems(pageable);
        // Errors are handled by GlobalExceptionHandler.
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
    @GetMapping(CART_ITEMS_GET_BY_CART) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<CartItem> getCartItemsByCartId(
            @PathVariable Long cartId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return cartService.findCartItemsByCartId(cartId, pageable);
        // Errors are handled by GlobalExceptionHandler.
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
    @GetMapping(CART_ITEMS_GET_BY_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<CartItem> getCartItemsByProductId(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return cartService.findCartItemsByProductId(productId, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find a specific cart item by cart ID and product ID.
     *
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting the CartItem.
     */
    @GetMapping(CART_ITEMS_FIND_SPECIFIC) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<CartItem> findSpecificCartItem(
            @PathVariable Long cartId,
            @PathVariable Long productId) {
        return cartService.findSpecificCartItem(cartId, productId);
        // Errors (ResourceNotFoundException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all cart items.
     *
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(CART_ITEMS_COUNT_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllCartItems() {
        return cartService.countAllCartItems();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all cart items for a specific cart.
     *
     * @param cartId The ID of the cart.
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(CART_ITEMS_COUNT_BY_CART) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countCartItemsByCartId(@PathVariable Long cartId) {
        return cartService.countCartItemsByCartId(cartId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all cart items for a specific product.
     *
     * @param productId The ID of the product.
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(CART_ITEMS_COUNT_BY_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countCartItemsByProductId(@PathVariable Long productId) {
        return cartService.countCartItemsByProductId(productId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to check if a specific product exists in a specific cart.
     *
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @return A Mono emitting true if it exists, false otherwise (Boolean).
     */
    @GetMapping(CART_ITEMS_CHECK_EXISTS) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> checkProductExistsInCart(
            @PathVariable Long cartId,
            @PathVariable Long productId) {
        return cartService.checkProductExistsInCart(cartId, productId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete all cart items for a given cart ID. This is a direct
     * deletion and should be used with caution. The /clear endpoint is
     * preferred for user-facing actions.
     *
     * @param cartId The ID of the cart.
     * @return A Mono<Void> indicating completion (results in 204 No Content).
     */
    @DeleteMapping(CART_ITEMS_ADMIN_DELETE_BY_CART) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteAllCartItemsByCartId(@PathVariable Long cartId) {
        return cartService.deleteAllCartItemsByCartId(cartId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a cart item by user ID and product ID. This is an
     * administrative endpoint or for specific use cases where a userId is
     * known.
     *
     * @param userId The ID of the user.
     * @param productId The ID of the product.
     * @return A Mono<Void> indicating completion (results in 204 No Content).
     */
    @DeleteMapping(CART_ITEMS_ADMIN_DELETE_BY_USER_AND_PRODUCT) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteCartItemByUserIdAndProductId(
            @PathVariable Long userId,
            @PathVariable Long productId) {
        return cartService.deleteCartItemByUserIdAndProductId(userId, productId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to directly update the quantity of a cart item by cart ID and
     * product ID. This is a direct update, potentially for administrative
     * purposes.
     *
     * @param cartId The ID of the cart.
     * @param productId The ID of the product.
     * @param payload A map containing the "quantity" to update.
     * @return A Mono emitting the number of rows updated (Integer).
     */
    @PutMapping(CART_ITEMS_ADMIN_DIRECT_UPDATE_QUANTITY) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Integer> directUpdateCartItemQuantity(
            @PathVariable Long cartId,
            @PathVariable Long productId,
            @RequestBody Map<String, Integer> payload) { // Changed to Integer as quantity is typically int
        Integer quantity = payload.get("quantity");

        if (quantity == null) {
            // This exception will be caught by GlobalExceptionHandler
            return Mono.error(new IllegalArgumentException("Quantity is required."));
        }
        // Quantity validation (min 0) is now in service.
        return cartService.directUpdateCartItemQuantity(quantity, cartId, productId);
        // Errors are handled by GlobalExceptionHandler.
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
    @GetMapping(CART_ADMIN_GET_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Flux<Cart> getAllCarts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return cartService.findAllCarts(pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find a cart by its associated user ID.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting the Cart.
     */
    @GetMapping(CART_GET_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Cart> getCartByUserId(@PathVariable Long userId) {
        return cartService.findCartByUserId(userId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all carts.
     *
     * @return A Mono emitting the count (Long).
     */
    @GetMapping(CART_COUNT_ALL) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countAllCarts() {
        return cartService.countAllCarts();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to check if a cart exists for a given user ID.
     *
     * @param userId The ID of the user.
     * @return A Mono emitting true if it exists, false otherwise (Boolean).
     */
    @GetMapping(CART_CHECK_EXISTS_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> checkCartExistsByUserId(@PathVariable Long userId) {
        return cartService.checkCartExistsByUserId(userId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a cart by user ID. This will also delete all cart
     * items associated with this cart.
     *
     * @param userId The ID of the user whose cart to delete.
     * @return A Mono<Void> indicating completion (results in 204 No Content).
     */
    @DeleteMapping(CART_ADMIN_DELETE_BY_USER) // MODIFIED
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteCartByUserId(@PathVariable Long userId) {
        return cartService.deleteCartByUserId(userId);
        // Errors are handled by GlobalExceptionHandler.
    }
}