package com.aliwudi.marketplace.backend.user.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode; // Keep for now, but will remove 'exclude' for cart
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;


@Table("users")
@Data
@NoArgsConstructor
@AllArgsConstructor
// IMPORTANT: Removed 'exclude = {"cart"}' as Cart is no longer part of this entity
@EqualsAndHashCode // If you have other fields you want to exclude, add them here.
public class User {

    @Id
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String shippingAddress;
    private String password; // Stores the *hashed* password    
    @Transient
    private Set<Role> roles = new HashSet<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}