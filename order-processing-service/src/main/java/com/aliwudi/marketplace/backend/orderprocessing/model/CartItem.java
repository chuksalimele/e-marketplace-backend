package com.aliwudi.marketplace.backend.orderprocessing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;   // NEW IMPORT
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // NEW IMPORT
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"cart"}) // EXCLUDE 'cart'
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many-to-One relationship with Cart
    // Multiple CartItems can belong to one Cart.
    // @JoinColumn specifies the foreign key column in the 'cart_items' table.
    // nullable = false means a CartItem must always belong to a Cart.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    @JsonBackReference // This side prevents infinite loop when serializing Cart -> CartItem
    private Cart cart;

    // Many-to-One relationship with Product
    // Multiple CartItems can refer to the same Product.
    // FetchType.LAZY to avoid loading product data unnecessarily.
    // nullable = false means a CartItem must always refer to a Product.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // NEW ADDITION
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    // Optional: Add a constructor for convenience
    public CartItem(Cart cart, Product product, Integer quantity) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
    }
}