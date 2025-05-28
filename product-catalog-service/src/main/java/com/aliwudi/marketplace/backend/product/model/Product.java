// Product.java
package com.aliwudi.marketplace.backend.product.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonBackReference;   // NEW IMPORT
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
// Make sure to adjust these excludes based on your actual bidirectional relationships.
// Assuming 'seller' and 'category' might be bidirectional and need exclusion from equals/hashCode.
@EqualsAndHashCode(exclude = {"store", "category"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    // NEW FIELD: Stock quantity
    @Column(nullable = false)
    private Integer stock; // Make sure this is initialized or set upon creation


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "products"}) // Assuming Category has a 'products' list
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    @JsonBackReference // Prevents infinite recursion when serializing Store -> Products -> Store
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "products"}) // Adjust as needed
    private Store store;

    // Constructor will also need to be updated
    public Product(String name, String description, BigDecimal price, Integer stock, Store store, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.store = store; // Changed from seller to store
        this.category = category;
    }
}