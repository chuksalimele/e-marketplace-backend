// RoleRepository.java
package com.aliwudi.marketplace.backend.user.repository;

import com.aliwudi.marketplace.backend.user.model.Role;
import com.aliwudi.marketplace.backend.common.enumeration.ERole; // NEW IMPORT
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

@Repository
public interface RoleRepository extends ReactiveCrudRepository<Role, Long> {
    // Change method signature to accept ERole
    Mono<Role> findByName(ERole name); // CHANGE HERE
}