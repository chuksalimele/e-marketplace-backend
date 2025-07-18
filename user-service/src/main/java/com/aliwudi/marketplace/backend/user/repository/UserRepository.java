package com.aliwudi.marketplace.backend.user.repository;

import com.aliwudi.marketplace.backend.common.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, Long> {

    // --- Basic CRUD operations are inherited from ReactiveCrudRepository ---

    //Flux<User> findAll(Pageable pageable);
    Flux<User> findAllBy(Pageable pageable);
        
    Mono<User> findByAuthId(String authId);
    
    // Find a user by phoneNumber
    Mono<User> findByPhoneNumber(String phoneNumber); 

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

    // Find users by phoneNumber or email, with pagination
    @Query("SELECT * FROM users WHERE phoneNumber ILIKE :searchTerm OR email ILIKE :searchTerm")
    Flux<User> findByPhoneNumberOrEmailContainingIgnoreCase(String searchTerm, Pageable pageable);

    // Count users by phoneNumber or email
    @Query("SELECT COUNT(*) FROM users WHERE phoneNumber ILIKE :searchTerm OR email ILIKE :searchTerm")
    Mono<Long> countByPhoneNumberOrEmailContainingIgnoreCase(String searchTerm);

    // Find users created after a certain date, with pagination
    Flux<User> findByCreatedAtAfter(java.time.LocalDateTime date, Pageable pageable);

    // Count users created after a certain date
    Mono<Long> countByCreatedAtAfter(java.time.LocalDateTime date);

    // Find users with a specific shipping address, with pagination
    Flux<User> findByShippingAddressContainingIgnoreCase(String shippingAddress, Pageable pageable);

    // Count users with a specific shipping address
    Mono<Long> countByShippingAddressContainingIgnoreCase(String shippingAddress);    

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByPhoneNumber(String phoneNumber);
    
    Mono<Boolean> existsByAuthId(String authId);
    
    @Modifying
    @Query("DELETE FROM users WHERE auth_id = :authId")
    Mono<Void> deleteByAuthId(String authId);    

    // Custom query to find a user by ID and fetch their roles
    // This query is a common pattern for loading relationships in R2DBC
    @Query("SELECT u.*, r.id AS role_id, r.name AS role_name " +
           "FROM users u " +
           "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
           "LEFT JOIN roles r ON ur.role_id = r.id " +
           "WHERE u.id = :id")
    Flux<User> findUserWithRolesById(Long id);

    // Custom query to find a user by phoneNumber and fetch their roles
    @Query("SELECT u.*, r.id AS role_id, r.name AS role_name " +
           "FROM users u " +
           "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
           "LEFT JOIN roles r ON ur.role_id = r.id " +
           "WHERE u.phoneNumber = :phoneNumber")
    Flux<User> findUserWithRolesByPhoneNumber(String phoneNumber);

    // Custom query to find a user by email and fetch their roles
    @Query("SELECT u.*, r.id AS role_id, r.name AS role_name " +
           "FROM users u " +
           "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
           "LEFT JOIN roles r ON ur.role_id = r.id " +
           "WHERE u.email = :email")
    Flux<User> findUserWithRolesByEmail(String email);    
}