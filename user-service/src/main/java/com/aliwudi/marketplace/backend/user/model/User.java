package com.aliwudi.marketplace.backend.user.model;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode; // Keep for now, but will remove 'exclude' for cart

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = "username"),
            @UniqueConstraint(columnNames = "email")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
// IMPORTANT: Removed 'exclude = {"cart"}' as Cart is no longer part of this entity
@EqualsAndHashCode // If you have other fields you want to exclude, add them here.
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String shippingAddress;

    @Column(nullable = false)
    private String password; // Stores the *hashed* password

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    // NEW: Instead of a direct @OneToOne relationship, store the ID of the cart.
    // This allows the User service to reference a cart in the separate Cart microservice.
    // Use a Long if your Cart IDs are Long, or String if they are UUIDs.
    @Column(name = "cart_id") // Add a column to store the ID of the user's cart
    private Long cartId; // Assuming Cart IDs are Longs. Adjust type if UUID (String)

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (roles.isEmpty()) {
            roles.add(new Role(ERole.ROLE_USER)); // Default role
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}