// UserRepository.java
package com.aliwudi.marketplace.backend.user.repository;

import com.aliwudi.marketplace.backend.user.model.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // NEW: Import ReactiveCrudRepository
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono; // NEW: Import Mono for single results or completion

// Remove old JpaRepository import and Optional import

@Repository
// NEW: Extend ReactiveCrudRepository instead of JpaRepository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    // ReactiveCrudRepository automatically provides reactive versions of save(), findById(), findAll(), deleteById().

    // Old: Optional<User> findByUsername(String username);
    // NEW: Returns Mono for zero or one result. An empty Mono means no user found.
    Mono<User> findByUsername(String username);

    // Old: Boolean existsByUsername(String username);
    // NEW: Returns Mono<Boolean> for the existence check.
    Mono<Boolean> existsByUsername(String username);

    // Old: Boolean existsByEmail(String email);
    // NEW: Returns Mono<Boolean> for the existence check.
    Mono<Boolean> existsByEmail(String email);
}