// RoleController.java
package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import com.aliwudi.marketplace.backend.common.model.Role;
import com.aliwudi.marketplace.backend.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService; // Although not directly used for listing all roles here,
                                           // it's good practice to keep it for future operations.

    /**
     * Retrieves all available roles.
     * @return Flux of RoleResponse representing all ERole values.
     */
    @GetMapping
    public Flux<Role> getAllRoles() {
        // Since roles are fixed by ERole enum, we can directly map them.
        // If roles were dynamically created/managed, you'd fetch them from roleRepository.
        return Flux.fromIterable(Arrays.asList(ERole.values()))
                .map(eRole -> new Role(null, eRole)); // ID is null as it's not fetched here
    }
}
