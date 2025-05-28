// Category.java
package com.aliwudi.marketplace.backend.product.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode; // Required for @EqualsAndHashCode.Exclude if you add it
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Required if you add it

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
// IMPORTANT: If Category later has a bidirectional relationship (e.g., Set<Product> products),
// you'll need to exclude that field here to prevent StackOverflowError in hashCode/equals.
// Example: @EqualsAndHashCode(exclude = {"products"})
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // Optional: If you want to have a list of products directly in the Category model
    // @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "category"}) // Exclude circular reference
    // @EqualsAndHashCode.Exclude // Exclude from equals/hashCode to prevent StackOverflowError
    // private Set<Product> products = new HashSet<>();

    public Category(String name) {
        this.name = name;
    }
}