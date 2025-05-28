package com.aliwudi.marketplace.backend.orderprocessing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
// Removed: import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // No longer needed as Product object is removed
import jakarta.persistence.*;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_items")
@Data
@NoArgsConstructor
// @AllArgsConstructor // Lombok will generate based on new fields, if you keep this
@EqualsAndHashCode(exclude = {"cart"}) // 'cart' remains excluded. 'product' was not explicitly excluded.
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many-to-One relationship with Cart (remains within the same service)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    @JsonBackReference // This side prevents infinite loop when serializing Cart -> CartItem
    private Cart cart;

    // --- REFACtORED CHANGE ---
    // Replaced direct Product object reference with its ID.
    // This productId acts as a foreign key that references the Product entity
    // in the separate Product Catalog Microservice's database.
    @Column(name = "product_id", nullable = false)
    private Long productId; // Changed from 'Product product' to 'Long productId'

    @Column(nullable = false)
    private Integer quantity;

    // --- REFACtORED CHANGE ---
    // Updated constructor to take productId instead of Product object.
    // If you're using Lombok's @AllArgsConstructor, it will automatically adjust.
    // If you have other custom constructors, update them similarly.
    public CartItem(Cart cart, Long productId, Integer quantity) {
        this.cart = cart;
        this.productId = productId;
        this.quantity = quantity;
    }

    // Lombok's @Data will generate getters and setters for 'id', 'cart', 'productId', and 'quantity'.
    // If you had any specific logic in custom 'getProduct()' or 'setProduct()' methods,
    // that logic will need to be moved to your CartService (or a dedicated service layer)
    // and adapted to use the productId and communicate with the Product Catalog Service.
}