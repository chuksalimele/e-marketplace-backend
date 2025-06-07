// RoleService.java
package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import com.aliwudi.marketplace.backend.common.model.Role;
import com.aliwudi.marketplace.backend.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor // Generates a constructor with required arguments (final fields)
public class RoleService  {

    private final RoleRepository roleRepository;

    /**
     * Finds a role by its enum name.
     * @param name The ERole enum representing the role name.
     * @return Mono of Role if found, Mono.empty() otherwise.
     */
    public Mono<Role> findByName(ERole name) {
        return roleRepository.findByName(name);
    }
}
