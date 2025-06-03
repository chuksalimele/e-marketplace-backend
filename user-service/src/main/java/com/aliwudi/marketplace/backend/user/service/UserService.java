package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.user.dto.UserUpdateRequest;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import com.aliwudi.marketplace.backend.user.repository.RoleRepository;
import com.aliwudi.marketplace.backend.user.exception.RoleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable; // Import for pagination

import java.time.LocalDateTime; // Import for date queries
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.role.ERole;
import com.aliwudi.marketplace.backend.user.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Retrieves all users.
     *
     * @return A Flux emitting all users.
     */
    public Flux<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param id The ID of the user.
     * @return A Mono emitting the user if found.
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Mono<User> getUserById(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)));
    }

    /**
     * Updates an existing user's details.
     *
     * @param id The ID of the user to update.
     * @param updateRequest The request containing updated user details.
     * @return A Mono emitting the updated User.
     * @throws ResourceNotFoundException if the user is not found.
     * @throws IllegalArgumentException if username/email is already taken or invalid role is provided.
     * @throws RoleNotFoundException if a specified role is not found.
     */
    public Mono<User> updateUser(Long id, UserUpdateRequest updateRequest) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(existingUser -> {
                    // Update fields only if provided in the request
                    if (updateRequest.getUsername() != null && !updateRequest.getUsername().isEmpty()) {
                        // Check if new username is already taken by another user
                        return userRepository.findByUsername(updateRequest.getUsername())
                                .flatMap(foundUser -> {
                                    if (!foundUser.getId().equals(existingUser.getId())) {
                                        return Mono.error(new IllegalArgumentException(ApiResponseMessages.USERNAME_ALREADY_TAKEN));
                                    }
                                    return Mono.just(existingUser); // Same user, no change needed for username
                                })
                                .switchIfEmpty(Mono.defer(() -> { // Username not found, so it's available
                                    existingUser.setUsername(updateRequest.getUsername());
                                    return Mono.just(existingUser);
                                }));
                    } else {
                        return Mono.just(existingUser);
                    }
                })
                .flatMap(existingUser -> {
                    if (updateRequest.getEmail() != null && !updateRequest.getEmail().isEmpty()) {
                        // Check if new email is already in use by another user
                        return userRepository.findByEmail(updateRequest.getEmail())
                                .flatMap(foundUser -> {
                                    if (!foundUser.getId().equals(existingUser.getId())) {
                                        return Mono.error(new IllegalArgumentException(ApiResponseMessages.EMAIL_ALREADY_IN_USE));
                                    }
                                    return Mono.just(existingUser); // Same user, no change needed for email
                                })
                                .switchIfEmpty(Mono.defer(() -> { // Email not found, so it's available
                                    existingUser.setEmail(updateRequest.getEmail());
                                    return Mono.just(existingUser);
                                }));
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

    /**
     * Deletes a user by their ID.
     *
     * @param id The ID of the user to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Mono<Void> deleteUser(Long id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(userRepository::delete);
    }

    // --- NEW: UserRepository Implementations ---

    /**
     * Finds a user by their username.
     *
     * @param username The username to search for.
     * @return A Mono emitting the User if found.
     */
    public Mono<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Finds a user by their email.
     *
     * @param email The email to search for.
     * @return A Mono emitting the User if found.
     */
    public Mono<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Finds users by first name, with pagination.
     *
     * @param firstName The first name to search for (case-insensitive).
     * @param pageable Pagination information.
     * @return A Flux of User records.
     */
    public Flux<User> findUsersByFirstNameContainingIgnoreCase(String firstName, Pageable pageable) {
        return userRepository.findByFirstNameContainingIgnoreCase(firstName, pageable);
    }

    /**
     * Counts users by first name.
     *
     * @param firstName The first name to count (case-insensitive).
     * @return A Mono emitting the count.
     */
    public Mono<Long> countUsersByFirstNameContainingIgnoreCase(String firstName) {
        return userRepository.countByFirstNameContainingIgnoreCase(firstName);
    }

    /**
     * Finds users by last name, with pagination.
     *
     * @param lastName The last name to search for (case-insensitive).
     * @param pageable Pagination information.
     * @return A Flux of User records.
     */
    public Flux<User> findUsersByLastNameContainingIgnoreCase(String lastName, Pageable pageable) {
        return userRepository.findByLastNameContainingIgnoreCase(lastName, pageable);
    }

    /**
     * Counts users by last name.
     *
     * @param lastName The last name to count (case-insensitive).
     * @return A Mono emitting the count.
     */
    public Mono<Long> countUsersByLastNameContainingIgnoreCase(String lastName) {
        return userRepository.countByLastNameContainingIgnoreCase(lastName);
    }

    /**
     * Finds users by username or email, with pagination.
     *
     * @param searchTerm The search term (username or email, case-insensitive).
     * @param pageable Pagination information.
     * @return A Flux of User records.
     */
    public Flux<User> findUsersByUsernameOrEmailContainingIgnoreCase(String searchTerm, Pageable pageable) {
        return userRepository.findByUsernameOrEmailContainingIgnoreCase(searchTerm, pageable);
    }

    /**
     * Counts users by username or email.
     *
     * @param searchTerm The search term (username or email, case-insensitive).
     * @return A Mono emitting the count.
     */
    public Mono<Long> countUsersByUsernameOrEmailContainingIgnoreCase(String searchTerm) {
        return userRepository.countByUsernameOrEmailContainingIgnoreCase(searchTerm);
    }

    /**
     * Finds users created after a certain date, with pagination.
     *
     * @param date The cutoff date.
     * @param pageable Pagination information.
     * @return A Flux of User records.
     */
    public Flux<User> findUsersByCreatedAtAfter(LocalDateTime date, Pageable pageable) {
        return userRepository.findByCreatedAtAfter(date, pageable);
    }

    /**
     * Counts users created after a certain date.
     *
     * @param date The cutoff date.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countUsersByCreatedAtAfter(LocalDateTime date) {
        return userRepository.countByCreatedAtAfter(date);
    }

    /**
     * Finds users with a specific shipping address, with pagination.
     *
     * @param shippingAddress The shipping address to search for (case-insensitive).
     * @param pageable Pagination information.
     * @return A Flux of User records.
     */
    public Flux<User> findUsersByShippingAddressContainingIgnoreCase(String shippingAddress, Pageable pageable) {
        return userRepository.findByShippingAddressContainingIgnoreCase(shippingAddress, pageable);
    }

    /**
     * Counts users with a specific shipping address.
     *
     * @param shippingAddress The shipping address to count (case-insensitive).
     * @return A Mono emitting the count.
     */
    public Mono<Long> countUsersByShippingAddressContainingIgnoreCase(String shippingAddress) {
        return userRepository.countByShippingAddressContainingIgnoreCase(shippingAddress);
    }

    // --- NEW: RoleRepository Implementations ---

    /**
     * Finds a role by its name.
     *
     * @param name The ERole enum representing the role name.
     * @return A Mono emitting the Role if found.
     */
    public Mono<com.aliwudi.marketplace.backend.user.model.Role> findRoleByName(ERole name) {
        return roleRepository.findByName(name);
    }

    /**
     * Finds roles with names containing a specific string (case-insensitive), with pagination.
     *
     * @param name The string to search for in role names.
     * @param pageable Pagination information.
     * @return A Flux of Role records.
     */
    public Flux<com.aliwudi.marketplace.backend.user.model.Role> findRolesByNameContainingIgnoreCase(String name, Pageable pageable) {
        return roleRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    /**
     * Counts roles with names containing a specific string (case-insensitive).
     *
     * @param name The string to count in role names.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countRolesByNameContainingIgnoreCase(String name) {
        return roleRepository.countByNameContainingIgnoreCase(name);
    }

    /**
     * Finds all roles with pagination.
     *
     * @param pageable Pagination information.
     * @return A Flux of Role records.
     */
    public Flux<com.aliwudi.marketplace.backend.user.model.Role> findAllRoles(Pageable pageable) {
        return roleRepository.findAllByIdNotNull(pageable);
    }

    /**
     * Counts all roles.
     *
     * @return A Mono emitting the total count of roles.
     */
    public Mono<Long> countAllRoles() {
        return roleRepository.count();
    }
}