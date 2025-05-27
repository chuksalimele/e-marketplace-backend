package com.marketplace.emarketplacebackend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "stores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"seller", "products"}) // Exclude bidirectional relationships
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., "Downtown Branch", "Online Warehouse"

    @Column(nullable = false)
    private String location; // e.g., "Lagos, Nigeria", "123 Main St, Anytown" - consider splitting into city, street, postal code etc.

    private String description;
    private String contactInfo;
    private String profileImageUrl;
    private Double rating; // Store-specific rating
    private String categories; // Categories specific to this store (if distinct from seller's overall categories)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    @JsonBackReference // Prevents infinite recursion when serializing Seller -> Stores -> Seller
    private Seller seller;

    @OneToMany(mappedBy = "store", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference // Ensures products are serialized when a store is fetched
    private Set<Product> products = new HashSet<>();

    // Optional: Constructor for convenience
    public Store(String name, String location, Seller seller) {
        this.name = name;
        this.location = location;
        this.seller = seller;
    }
}