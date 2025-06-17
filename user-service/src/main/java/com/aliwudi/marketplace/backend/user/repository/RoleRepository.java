// RoleRepository.java
package com.aliwudi.marketplace.backend.user.repository;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import com.aliwudi.marketplace.backend.common.model.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RoleRepository extends R2dbcRepository<Role, Long> {

    // --- Basic CRUD operations are inherited from ReactiveCrudRepository ---

    // Find a role by its name
    Mono<Role> findByName(String name);
    
    /**
     * Finds all roles associated with a given user ID.
     * This assumes a many-to-many relationship between users and roles
     * through a linking table named 'user_roles' (or similar).
     * The query joins 'roles' table with 'user_roles' table on 'role_id'
     * and filters by 'user_id'.
     *
     * @param userId The ID of the user.
     * @return A Flux emitting the roles associated with the given user ID.
     */
    @Query("SELECT r.id, r.name FROM roles r JOIN user_roles ur ON r.id = ur.role_id WHERE ur.user_id = :userId")
    Flux<Role> findRolesByUserId(Long userId);
    

    // Find roles with names containing a specific string (case-insensitive), with pagination
    // Note: This assumes ERole has a meaningful String representation for ILIKE to work,
    // or you might need a custom query if ERole is just an enum without a direct string column mapping for search.
    // For simplicity, we'll assume ERole.name() works for comparison here.
    Flux<Role> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // Count roles with names containing a specific string (case-insensitive)
    Mono<Long> countByNameContainingIgnoreCase(String name);

    // Find all roles with pagination
    Flux<Role> findAllByIdNotNull(Pageable pageable);

    // Count all roles
    Mono<Long> count();

}