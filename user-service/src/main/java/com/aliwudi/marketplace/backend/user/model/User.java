package com.aliwudi.marketplace.backend.user.model;

import com.fasterxml.jackson.annotation.JsonManagedReference; // NEW IMPORT
import jakarta.persistence.*; // Essential JPA annotations
import lombok.Data;          // Lombok
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor; // Lombok
import lombok.AllArgsConstructor; // Lombok

import java.time.LocalDateTime;
import java.util.HashSet;    // For storing roles in a Set
import java.util.Set;      // For storing roles in a Set

@Entity // Marks this class as a JPA entity, mapping to a database table
@Table(name = "users", // Specifies the actual table name in the database as "users"
                       // (Using "users" instead of default "user" to avoid potential SQL keyword conflicts)
       uniqueConstraints = { // Ensures that `username` and `email` values are unique across all users
           @UniqueConstraint(columnNames = "username"),
           @UniqueConstraint(columnNames = "email")
       })
@Data // From Lombok: Generates getters, setters, etc.
@NoArgsConstructor // From Lombok: Generates an empty constructor
@AllArgsConstructor // From Lombok: Generates a constructor with all fields
@EqualsAndHashCode(exclude = {"cart"}) // EXCLUDE 'cart'
public class User {

    @Id // Marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increments the ID
    private Long id; // Unique identifier for each user

    @Column(nullable = false, unique = true) // Username cannot be null and must be unique
    private String username;

    @Column(nullable = false, unique = true) // Email cannot be null and must be unique
    private String email;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String shippingAddress; // Primary shipping address
    

    @Column(nullable = false) // Password cannot be null
    private String password; // IMPORTANT: This will store the *hashed* password, never plain text!

    // Defines a Many-to-Many relationship between User and Role entities.
    // A user can have multiple roles, and a role can be assigned to multiple users.
    @ManyToMany(fetch = FetchType.LAZY) // Roles are typically fetched lazily (only when explicitly requested)
    @JoinTable(name = "user_roles", // Specifies the name of the "junction" or "join" table that links users and roles
               joinColumns = @JoinColumn(name = "user_id"), // Column in "user_roles" that references the User's ID
               inverseJoinColumns = @JoinColumn(name = "role_id")) // Column in "user_roles" that references the Role's ID
    private Set<Role> roles = new HashSet<>(); // A Set is used to store roles, preventing duplicate roles for a user

    // NEW ADDITION: One-to-One relationship with Cart
    // mappedBy refers to the 'user' field in the Cart entity.
    // CascadeType.ALL ensures that if a User is deleted, their Cart is also deleted.
    // orphanRemoval = true ensures that if a Cart is detached from the User, it's removed from the DB.
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference // This side is the "owner" for serialization
    private Cart cart;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Lifecycle callbacks to manage creation and update timestamps
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

    // A convenience constructor for registering a new user (before roles are assigned)
    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}