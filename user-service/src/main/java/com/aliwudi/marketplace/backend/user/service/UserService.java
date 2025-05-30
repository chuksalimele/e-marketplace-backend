package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.user.dto.UserUpdateRequest;
import com.aliwudi.marketplace.backend.user.model.User;
import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import com.aliwudi.marketplace.backend.user.repository.RoleRepository;
import com.aliwudi.marketplace.backend.user.exception.RoleNotFoundException; // Import your custom exception
import com.aliwudi.marketplace.backend.orderprocessing.exception.ResourceNotFoundException; // Reusing this exception
import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages; // For messages in service
import com.aliwudi.marketplace.backend.user.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public Flux<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Mono<User> getUserById(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)));
    }

    public Mono<User> updateUser(Long id, UserUpdateRequest updateRequest) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(existingUser -> {
                    // Update fields only if provided in the request
                    if (updateRequest.getUsername() != null && !updateRequest.getUsername().isEmpty()) {
                        // Check if new username is already taken by another user
                        return userRepository.existsByUsername(updateRequest.getUsername())
                                .flatMap(usernameExists -> {
                                    if (usernameExists && !existingUser.getUsername().equals(updateRequest.getUsername())) {
                                        return Mono.error(new IllegalArgumentException(ApiResponseMessages.USERNAME_ALREADY_TAKEN));
                                    }
                                    existingUser.setUsername(updateRequest.getUsername());
                                    return Mono.just(existingUser);
                                });
                    } else {
                        return Mono.just(existingUser);
                    }
                })
                .flatMap(existingUser -> {
                    if (updateRequest.getEmail() != null && !updateRequest.getEmail().isEmpty()) {
                        // Check if new email is already in use by another user
                        return userRepository.existsByEmail(updateRequest.getEmail())
                                .flatMap(emailExists -> {
                                    if (emailExists && !existingUser.getEmail().equals(updateRequest.getEmail())) {
                                        return Mono.error(new IllegalArgumentException(ApiResponseMessages.EMAIL_ALREADY_IN_USE));
                                    }
                                    existingUser.setEmail(updateRequest.getEmail());
                                    return Mono.just(existingUser);
                                });
                    } else {
                        return Mono.just(existingUser);
                    }
                })
                .flatMap(existingUser -> {
                    if (updateRequest.getPassword() != null && !updateRequest.getPassword().isEmpty()) {
                        existingUser.setPassword(passwordEncoder.encode(updateRequest.getPassword()));
                    }
                    return Mono.just(existingUser);
                })
                .flatMap(existingUser -> {
                    if (updateRequest.getRoles() != null && !updateRequest.getRoles().isEmpty()) {
                        return Flux.fromIterable(updateRequest.getRoles())
                                .flatMap(roleName -> {
                                    ERole eRole;
                                    switch (roleName.toLowerCase()) {
                                        case "admin": eRole = ERole.ROLE_ADMIN; break;
                                        case "seller": eRole = ERole.ROLE_SELLER; break;
                                        case "user": eRole = ERole.ROLE_USER; break;
                                        default: return Mono.error(new IllegalArgumentException(ApiResponseMessages.INVALID_ROLE_PROVIDED + roleName));
                                    }
                                    return roleRepository.findByName(eRole)
                                            .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND + eRole.name())));
                                })
                                .collect(Collectors.toSet())
                                .map(roles -> {
                                    existingUser.setRoles(roles);
                                    return existingUser;
                                });
                    } else {
                        return Mono.just(existingUser);
                    }
                })
                .flatMap(userRepository::save); // Save the updated user
    }

    public Mono<Void> deleteUser(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(userRepository::delete);
    }
}