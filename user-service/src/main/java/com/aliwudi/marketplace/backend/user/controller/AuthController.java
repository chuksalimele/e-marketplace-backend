package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.user.model.Role;
import com.aliwudi.marketplace.backend.user.dto.MessageResponse;
import com.aliwudi.marketplace.backend.common.role.ERole;
import com.aliwudi.marketplace.backend.user.model.User;
import com.aliwudi.marketplace.backend.user.dto.SignupRequest;
import com.aliwudi.marketplace.backend.user.repository.RoleRepository;
import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors; // Needed for collectList if you use it directly after map

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;

    @Autowired
    public AuthController(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
    }

    @PostMapping("/signup")
    public Mono<ResponseEntity<MessageResponse>> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        // Check if username already exists reactively
        return userRepository.existsByUsername(signUpRequest.getUsername())
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Mono.just(new ResponseEntity<>(new MessageResponse("Error: Username is already taken!"), HttpStatus.BAD_REQUEST));
                    }
                    // If username does not exist, then check email existence.
                    // This is the correct way to chain an asynchronous check.
                    return userRepository.existsByEmail(signUpRequest.getEmail())
                            .flatMap(emailExists -> { // <-- Correctly unwrap emailExists Mono<Boolean>
                                if (emailExists) { // Now emailExists is a boolean
                                    return Mono.just(new ResponseEntity<>(new MessageResponse("Error: Email is already in use!"), HttpStatus.BAD_REQUEST));
                                }

                                // Create new user's account
                                User user = new User(signUpRequest.getUsername(),
                                        signUpRequest.getEmail(),
                                        encoder.encode(signUpRequest.getPassword())); // Hash the password!

                                Set<String> strRoles = signUpRequest.getRole();
                                Set<Role> roles = new HashSet<>();

                                // Logic to assign roles.
                                // We need to ensure that the roles are fetched reactively and
                                // the `roles` Set is populated before `userRepository.save(user)` is called.

                                // This will return a Mono<Set<Role>> or Mono<Role> depending on single/multiple
                                Mono<Set<Role>> fetchedRolesMono;

                                if (strRoles == null || strRoles.isEmpty()) {
                                    // Default to ROLE_USER if no role is specified or the set is empty
                                    fetchedRolesMono = roleRepository.findByName(ERole.ROLE_USER)
                                            .switchIfEmpty(Mono.error(new RuntimeException("Error: Role 'ROLE_USER' is not found.")))
                                            .map(role -> {
                                                Set<Role> defaultRoles = new HashSet<>();
                                                defaultRoles.add(role);
                                                return defaultRoles;
                                            });
                                } else {
                                    // Handle multiple roles reactively
                                    fetchedRolesMono = Flux.fromIterable(strRoles)
                                            .flatMap(roleName -> {
                                                ERole eRole;
                                                switch (roleName.toLowerCase()) {
                                                    case "admin":
                                                        eRole = ERole.ROLE_ADMIN;
                                                        break;
                                                    case "seller":
                                                        eRole = ERole.ROLE_SELLER;
                                                        break;
                                                    default:
                                                        eRole = ERole.ROLE_USER;
                                                        break;
                                                }
                                                return roleRepository.findByName(eRole)
                                                        .switchIfEmpty(Mono.error(new RuntimeException("Error: Role '" + eRole.name() + "' is not found.")));
                                            })
                                            .collect(Collectors.toSet()); // Collect into a Set<Role>
                                }

                                return fetchedRolesMono
                                        .flatMap(assignedRoles -> { // FlatMap to work with the fetched roles
                                            user.setRoles(assignedRoles); // Set the roles for the new user
                                            return userRepository.save(user); // Save the new user to the database
                                        })
                                        .thenReturn(new ResponseEntity<>(new MessageResponse("User registered successfully!"), HttpStatus.OK))
                                        .onErrorResume(RuntimeException.class, e ->
                                                Mono.just(new ResponseEntity<>(new MessageResponse(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR)));
                            });
                });
    }
}