// RoleRepository.java
package com.aliwudi.marketplace.backend.user.repository;

import com.aliwudi.marketplace.backend.common.role.ERole;
import com.aliwudi.marketplace.backend.user.model.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RoleRepository extends ReactiveCrudRepository<Role, Long> {

    // --- Basic CRUD operations are inherited from ReactiveCrudRepository ---

    // Find a role by its name (ERole enum)
    Mono<Role> findByName(ERole name);

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