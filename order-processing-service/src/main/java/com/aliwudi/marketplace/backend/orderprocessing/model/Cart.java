package com.aliwudi.marketplace.backend.orderprocessing.model;

// Removed: import com.fasterxml.jackson.annotation.JsonBackReference; // No longer needed as User object is removed
import com.fasterxml.jackson.annotation.JsonManagedReference; // Still needed for CartItem
import jakarta.persistence.*;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "carts")
@Data
@NoArgsConstructor // Lombok will generate default no-args constructor
// AllArgsConstructor might need manual adjustment or removal if you only want specific constructors
// @AllArgsConstructor // Lombok will generate an all-args constructor including userId
@EqualsAndHashCode(exclude = {"items"}) // 'user' removed from exclude, as it's no longer a direct field. 'items' remains.
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- REFACtORED CHANGE ---
    // Instead of a direct User object, we now store only the ID of the user.
    // This userId acts as a foreign key that references the User entity
    // in the separate User Microservice's database.
    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId; // Renamed from 'user' to 'userId' and type changed from User to Long

    // One-to-Many relationship with CartItem - this remains the same as CartItem is assumed to be in the same service
    // A cart can have multiple cart items.
    // mappedBy refers to the 'cart' field in the CartItem entity.
    // CascadeType.ALL ensures that if a Cart is deleted, all its CartItems are also deleted.
    // orphanRemoval = true ensures that if a CartItem is removed from the 'items' set, it's deleted from the DB.
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference // This side is the "owner" for serialization
    private Set<CartItem> items = new HashSet<>(); // Use a Set to prevent duplicate cart items

    // --- REFACtORED CHANGE ---
    // You might want to update or add constructors that take a userId.
    // If you use @AllArgsConstructor from Lombok, it will automatically include 'userId' now.
    // If you had a custom constructor taking 'User', it should now take 'Long userId'.
    public Cart(Long userId) {
        this.userId = userId;
        this.items = new HashSet<>(); // Initialize items in this constructor too
    }

    // Lombok's @Data will generate getters and setters for 'id', 'userId', and 'items'.
    // If you had any specific logic in custom 'getUser()' or 'setUser()' methods,
    // that logic will need to be moved to your CartService (or a dedicated service layer)
    // and adapted to use the userId and communicate with the User Service.
}