// UserRepository.java
package com.marketplace.emarketplacebackend.repository;

import com.marketplace.emarketplacebackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository; // Provides basic CRUD operations
import org.springframework.stereotype.Repository; // Marks this as a Spring Repository component

import java.util.Optional; // Used for methods that might return no result

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // JpaRepository automatically provides methods like save(), findById(), findAll(), deleteById().

    // Custom query method: Find a User by their username. Returns Optional to handle cases where user is not found.
    Optional<User> findByUsername(String username);

    // Custom query method: Check if a user with the given username already exists.
    Boolean existsByUsername(String username);

    // Custom query method: Check if a user with the given email already exists.
    Boolean existsByEmail(String email);
}