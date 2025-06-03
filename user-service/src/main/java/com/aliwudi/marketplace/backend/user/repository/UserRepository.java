package com.aliwudi.marketplace.backend.user.repository;

import com.aliwudi.marketplace.backend.user.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, Long> {

    // --- Basic CRUD operations are inherited from ReactiveCrudRepository ---

    public Flux<User> findAll(Pageable pageable);
    
    // Find a user by username
    Mono<User> findByUsername(String username);

    // Find a user by email
    Mono<User> findByEmail(String email);

    // Find users by first name, with pagination
    Flux<User> findByFirstNameContainingIgnoreCase(String firstName, Pageable pageable);

    // Count users by first name
    Mono<Long> countByFirstNameContainingIgnoreCase(String firstName);

    // Find users by last name, with pagination
    Flux<User> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);

    // Count users by last name
    Mono<Long> countByLastNameContainingIgnoreCase(String lastName);

    // Find users by username or email, with pagination
    @Query("SELECT * FROM users WHERE username ILIKE :searchTerm OR email ILIKE :searchTerm")
    Flux<User> findByUsernameOrEmailContainingIgnoreCase(String searchTerm, Pageable pageable);

    // Count users by username or email
    @Query("SELECT COUNT(*) FROM users WHERE username ILIKE :searchTerm OR email ILIKE :searchTerm")
    Mono<Long> countByUsernameOrEmailContainingIgnoreCase(String searchTerm);

    // Find users created after a certain date, with pagination
    Flux<User> findByCreatedAtAfter(java.time.LocalDateTime date, Pageable pageable);

    // Count users created after a certain date
    Mono<Long> countByCreatedAtAfter(java.time.LocalDateTime date);

    // Find users with a specific shipping address, with pagination
    Flux<User> findByShippingAddressContainingIgnoreCase(String shippingAddress, Pageable pageable);

    // Count users with a specific shipping address
    Mono<Long> countByShippingAddressContainingIgnoreCase(String shippingAddress);    

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByUsername(String username);

}