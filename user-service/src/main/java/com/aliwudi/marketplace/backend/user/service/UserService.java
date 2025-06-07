package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.common.enumeration.ERole;
import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import com.aliwudi.marketplace.backend.user.repository.RoleRepository;
import com.aliwudi.marketplace.backend.user.dto.PasswordUpdateRequest;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.model.Role;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.common.exception.InvalidPasswordException;
import com.aliwudi.marketplace.backend.common.exception.RoleNotFoundException; // Assuming this custom exception exists or using ResourceNotFoundException

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // For transactional operations
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;

@Service
@RequiredArgsConstructor // Generates a constructor for final fields (UserRepository, RoleRepository, PasswordEncoder)
@Slf4j // Enables Lombok's logging
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // IMPORTANT: This prepareDto method is for enriching the User model.
    // It is placed here as per your instruction to move it to an appropriate location
    // and is not modified. It enriches the User object with its associated roles.
    /**
     * Helper method to map User entity to User DTO for public exposure.
     * This method enriches the User object with its associated Role details.
     * Assumes that the User model has a 'Set<Role> roles' field that can be set.
     */
    private Mono<User> prepareDto(User user) {
        if (user == null) {
            return Mono.empty();
        }

        // Fetch roles for the user if not already set or partially loaded
        // This assumes a Many-to-Many relationship between User and Role,
        // and that User has a method like 'setRoles'.
        // Also assuming RoleRepository has a method to find roles by user ID if roles are not directly loaded.
        if (user.getRoles() == null || user.getRoles().isEmpty() && user.getId() != null) {
            return roleRepository.findRolesByUserId(user.getId()) // Assuming this method exists in RoleRepository
                    .collectList()
                    .doOnNext(roles -> user.setRoles(new HashSet<>(roles))) // Convert List to HashSet
                    .then(Mono.just(user))
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch roles for user {}: {}", user.getId(), e.getMessage());
                        user.setRoles(new HashSet<>()); // Set empty set if fetching fails
                        return Mono.just(user); // Continue with the user object
                    });
        }
        return Mono.just(user);
    }

    /**
     * Creates a new user in the system.
     * Performs checks for duplicate username and email, hashes the password,
     * and assigns default roles if none are specified, or specific roles if provided.
     * This operation is transactional.
     *
     * @param userRequest The DTO containing user creation data.
     * @return A Mono emitting the created User (enriched with roles).
     * @throws DuplicateResourceException if username or email already exist.
     * @throws RoleNotFoundException if any specified role does not exist.
     */
    @Transactional
    public Mono<User> createUser(UserRequest userRequest) {
        log.info("Attempting to create user with username: {} and email: {}", userRequest.getUsername(), userRequest.getEmail());

        // Check for duplicate username and email concurrently
        Mono<Boolean> usernameExists = userRepository.existsByUsername(userRequest.getUsername());
        Mono<Boolean> emailExists = userRepository.existsByEmail(userRequest.getEmail());

        return Mono.zip(usernameExists, emailExists)
                .flatMap(tuple -> {
                    boolean isUsernameTaken = tuple.getT1();
                    boolean isEmailInUse = tuple.getT2();

                    if (isUsernameTaken) {
                        log.warn("Username already taken: {}", userRequest.getUsername());
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.USERNAME_ALREADY_TAKEN));
                    }
                    if (isEmailInUse) {
                        log.warn("Email already in use: {}", userRequest.getEmail());
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.EMAIL_ALREADY_IN_USE));
                    }

                    // Build user object
                    User user = User.builder()
                            .username(userRequest.getUsername())
                            .email(userRequest.getEmail())
                            .password(passwordEncoder.encode(userRequest.getPassword())) // Hash password
                            .firstName(userRequest.getFirstName())
                            .lastName(userRequest.getLastName())
                            .shippingAddress(userRequest.getShippingAddress())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    // Resolve roles
                    Mono<Set<Role>> rolesMono;
                    if (userRequest.getRoleNames() != null && !userRequest.getRoleNames().isEmpty()) {
                        rolesMono = Flux.fromIterable(userRequest.getRoleNames())
                                .flatMap(roleName -> {
                                    try {
                                        ERole eRole = ERole.valueOf(roleName.toUpperCase());
                                        return roleRepository.findByName(eRole)
                                                .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND_MSG + " " + roleName)));
                                    } catch (IllegalArgumentException e) {
                                        return Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND_MSG + " " + roleName));
                                    }
                                })
                                .collect(Collectors.toSet());
                    } else {
                        // Default to ROLE_USER if no roles specified
                        rolesMono = roleRepository.findByName(ERole.ROLE_USER)
                                .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND_MSG + " " + ERole.ROLE_USER.name())))
                                .map(Set::of); // Wrap single role in a Set
                    }

                    return rolesMono // This is Mono<Set<Role>>
                            .flatMap(roles -> { // 'roles' here is the Set<Role> obtained from rolesMono
                                user.setRoles(roles); // Set roles on the user object
                                return userRepository.save(user); // Save the user
                            })
                            .flatMap(this::prepareDto) // Enrich the saved user
                            .doOnSuccess(u -> log.info("User created successfully with ID: {}", u.getId()))
                            .doOnError(e -> log.error("Error creating user {}: {}", userRequest.getUsername(), e.getMessage(), e));
                });
    }

    /**
     * Updates an existing user's information.
     * Handles partial updates and ensures email/username uniqueness if changed.
     * This operation is transactional.
     *
     * @param id The ID of the user to update.
     * @param userRequest The DTO containing updated user data.
     * @return A Mono emitting the updated User (enriched).
     * @throws ResourceNotFoundException if the user is not found.
     * @throws DuplicateResourceException if updated username or email already exist.
     * @throws RoleNotFoundException if any specified role does not exist during role update.
     */
    @Transactional
    public Mono<User> updateUser(Long id, UserRequest userRequest) {
        log.info("Attempting to update user with ID: {}", id);
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(existingUser -> {
                    // Check for duplicate username if changed
                    Mono<Void> usernameCheck = Mono.empty();
                    if (userRequest.getUsername() != null && !userRequest.getUsername().isBlank() &&
                        !existingUser.getUsername().equalsIgnoreCase(userRequest.getUsername())) {
                        usernameCheck = userRepository.existsByUsername(userRequest.getUsername())
                                .flatMap(exists -> {
                                    if (exists) {
                                        log.warn("Attempt to update username to an existing one: {}", userRequest.getUsername());
                                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.USERNAME_ALREADY_TAKEN));
                                    }
                                    return Mono.empty();
                                });
                    }

                    // Check for duplicate email if changed
                    Mono<Void> emailCheck = Mono.empty();
                    if (userRequest.getEmail() != null && !userRequest.getEmail().isBlank() &&
                        !existingUser.getEmail().equalsIgnoreCase(userRequest.getEmail())) {
                        emailCheck = userRepository.existsByEmail(userRequest.getEmail())
                                .flatMap(exists -> {
                                    if (exists) {
                                        log.warn("Attempt to update email to an existing one: {}", userRequest.getEmail());
                                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.EMAIL_ALREADY_IN_USE));
                                    }
                                    return Mono.empty();
                                });
                    }

                    // Combine checks
                    return Mono.when(usernameCheck, emailCheck)
                            .thenReturn(existingUser); // Pass the existing user along
                })
                .flatMap(existingUser -> {
                    // Apply updates
                    if (userRequest.getUsername() != null && !userRequest.getUsername().isBlank()) {
                        existingUser.setUsername(userRequest.getUsername());
                    }
                    if (userRequest.getEmail() != null && !userRequest.getEmail().isBlank()) {
                        existingUser.setEmail(userRequest.getEmail());
                    }
                    if (userRequest.getFirstName() != null && !userRequest.getFirstName().isBlank()) {
                        existingUser.setFirstName(userRequest.getFirstName());
                    }
                    if (userRequest.getLastName() != null && !userRequest.getLastName().isBlank()) {
                        existingUser.setLastName(userRequest.getLastName());
                    }
                    if (userRequest.getShippingAddress() != null && !userRequest.getShippingAddress().isBlank()) {
                        existingUser.setShippingAddress(userRequest.getShippingAddress());
                    }
                    existingUser.setUpdatedAt(LocalDateTime.now());

                    Mono<User> userSaveMono = Mono.just(existingUser);

                    // Update roles if provided
                    if (userRequest.getRoleNames() != null) { // If roleNames is provided (can be empty set to clear roles)
                        userSaveMono = Flux.fromIterable(userRequest.getRoleNames())
                                .flatMap(roleName -> {
                                    try {
                                        ERole eRole = ERole.valueOf(roleName.toUpperCase());
                                        return roleRepository.findByName(eRole)
                                                .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND_MSG + " " + roleName)));
                                    } catch (IllegalArgumentException e) {
                                        return Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND_MSG + " " + roleName));
                                    }
                                })
                                .collect(Collectors.toSet())
                                .flatMap(roles -> {
                                    existingUser.setRoles(roles);
                                    return userRepository.save(existingUser);
                                });
                    } else {
                        // If roleNames is null, save without changing roles on the user object itself (assuming R2DBC handles this naturally).
                        // If roles should be explicitly kept as they were, no action is needed here besides saving the user.
                        // If roleNames is an empty set, `collect(Collectors.toSet())` would produce an empty set,
                        // effectively clearing roles on `existingUser.setRoles(roles);`
                        userSaveMono = userRepository.save(existingUser);
                    }

                    return userSaveMono;
                })
                .flatMap(this::prepareDto) // Enrich the updated user
                .doOnSuccess(u -> log.info("User updated successfully with ID: {}", u.getId()))
                .doOnError(e -> log.error("Error updating user {}: {}", id, e.getMessage(), e));
    }

    /**
     * Updates a user's password.
     * Checks if the old password matches the current one before updating.
     * This operation is transactional.
     *
     * @param id The ID of the user whose password to update.
     * @param passwordUpdateRequest DTO containing old and new passwords.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the user is not found.
     * @throws InvalidPasswordException if the old password does not match or new password is too short/weak.
     */
    @Transactional
    public Mono<Void> updateUserPassword(Long id, PasswordUpdateRequest passwordUpdateRequest) {
        log.info("Attempting to update password for user with ID: {}", id);
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(existingUser -> {
                    if (!passwordEncoder.matches(passwordUpdateRequest.getOldPassword(), existingUser.getPassword())) {
                        log.warn("Old password mismatch for user ID: {}", id);
                        return Mono.error(new InvalidPasswordException(ApiResponseMessages.OLD_PASSWORD_MISMATCH));
                    }

                    // Password strength validation from DTO's @Size annotation is typically enough
                    // but you could add more complex logic here if needed.

                    existingUser.setPassword(passwordEncoder.encode(passwordUpdateRequest.getNewPassword()));
                    existingUser.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(existingUser);
                })
                .then() // Convert to Mono<Void> after the save operation completes
                .doOnSuccess(v -> log.info("Password updated successfully for user ID: {}", id))
                .doOnError(e -> log.error("Error updating password for user {}: {}", id, e.getMessage(), e));
    }

    /**
     * Deletes a user by their ID.
     * This operation is transactional.
     *
     * @param id The ID of the user to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @Transactional
    public Mono<Void> deleteUser(Long id) {
        log.info("Attempting to delete user with ID: {}", id);
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(userRepository::delete)
                .doOnSuccess(v -> log.info("User deleted successfully with ID: {}", id))
                .doOnError(e -> log.error("Error deleting user {}: {}", id, e.getMessage(), e));
    }

    /**
     * Retrieves a user by their ID, enriching them.
     *
     * @param id The ID of the user to retrieve.
     * @return A Mono emitting the User if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Mono<User> getUserById(Long id) {
        log.info("Retrieving user by ID: {}", id);
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(this::prepareDto) // Enrich the user
                .doOnSuccess(user -> log.info("User retrieved successfully: {}", user.getId()))
                .doOnError(e -> log.error("Error retrieving user {}: {}", id, e.getMessage(), e));
    }

    /**
     * Retrieves a user by their username, enriching them.
     *
     * @param username The username of the user.
     * @return A Mono emitting the User if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Mono<User> getUserByUsername(String username) {
        log.info("Retrieving user by username: {}", username);
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_USERNAME + username)))
                .flatMap(this::prepareDto) // Enrich the user
                .doOnSuccess(user -> log.info("User retrieved successfully by username: {}", user.getUsername()))
                .doOnError(e -> log.error("Error retrieving user by username {}: {}", username, e.getMessage(), e));
    }

    /**
     * Retrieves a user by their email, enriching them.
     *
     * @param email The email of the user.
     * @return A Mono emitting the User if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Mono<User> getUserByEmail(String email) {
        log.info("Retrieving user by email: {}", email);
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_EMAIL + email)))
                .flatMap(this::prepareDto) // Enrich the user
                .doOnSuccess(user -> log.info("User retrieved successfully by email: {}", user.getEmail()))
                .doOnError(e -> log.error("Error retrieving user by email {}: {}", email, e.getMessage(), e));
    }

    /**
     * Retrieves all users with pagination, enriching each.
     *
     * @param pageable Pagination information.
     * @return A Flux emitting all users (enriched).
     */
    public Flux<User> getAllUsers(Pageable pageable) {
        log.info("Retrieving all users with pagination: {}", pageable);
        return userRepository.findAll(pageable)
                .flatMap(this::prepareDto) // Enrich each user
                .doOnComplete(() -> log.info("Finished retrieving all users for page {} with size {}.", pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving all users: {}", e.getMessage(), e));
    }

    /**
     * Counts all users.
     *
     * @return A Mono emitting the total count of users.
     */
    public Mono<Long> countAllUsers() {
        log.info("Counting all users.");
        return userRepository.count()
                .doOnSuccess(count -> log.info("Total user count: {}", count))
                .doOnError(e -> log.error("Error counting all users: {}", e.getMessage(), e));
    }

    /**
     * Finds users by first name (case-insensitive, contains) with pagination, enriching each.
     *
     * @param firstName The first name to search for.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByFirstName(String firstName, Pageable pageable) {
        log.info("Finding users by first name '{}' with pagination: {}", firstName, pageable);
        return userRepository.findByFirstNameContainingIgnoreCase(firstName, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished finding users by first name '{}' for page {} with size {}.", firstName, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by first name {}: {}", firstName, e.getMessage(), e));
    }

    /**
     * Counts users by first name (case-insensitive, contains).
     *
     * @param firstName The first name to search for.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByFirstName(String firstName) {
        log.info("Counting users by first name '{}'", firstName);
        return userRepository.countByFirstNameContainingIgnoreCase(firstName)
                .doOnSuccess(count -> log.info("Total count for first name '{}': {}", firstName, count))
                .doOnError(e -> log.error("Error counting users by first name {}: {}", firstName, e.getMessage(), e));
    }

    /**
     * Finds users by last name (case-insensitive, contains) with pagination, enriching each.
     *
     * @param lastName The last name to search for.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByLastName(String lastName, Pageable pageable) {
        log.info("Finding users by last name '{}' with pagination: {}", lastName, pageable);
        return userRepository.findByLastNameContainingIgnoreCase(lastName, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished finding users by last name '{}' for page {} with size {}.", lastName, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by last name {}: {}", lastName, e.getMessage(), e));
    }

    /**
     * Counts users by last name (case-insensitive, contains).
     *
     * @param lastName The last name to search for.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByLastName(String lastName) {
        log.info("Counting users by last name '{}'", lastName);
        return userRepository.countByLastNameContainingIgnoreCase(lastName)
                .doOnSuccess(count -> log.info("Total count for last name '{}': {}", lastName, count))
                .doOnError(e -> log.error("Error counting users by last name {}: {}", lastName, e.getMessage(), e));
    }

    /**
     * Finds users by username or email (case-insensitive, contains) with pagination, enriching each.
     *
     * @param searchTerm The search term for username or email.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByUsernameOrEmail(String searchTerm, Pageable pageable) {
        log.info("Finding users by username or email containing '{}' with pagination: {}", searchTerm, pageable);
        return userRepository.findByUsernameOrEmailContainingIgnoreCase(searchTerm, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished finding users by username or email containing '{}' for page {} with size {}.", searchTerm, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by username or email {}: {}", searchTerm, e.getMessage(), e));
    }

    /**
     * Counts users by username or email (case-insensitive, contains).
     *
     * @param searchTerm The search term for username or email.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByUsernameOrEmail(String searchTerm) {
        log.info("Counting users by username or email containing '{}'", searchTerm);
        return userRepository.countByUsernameOrEmailContainingIgnoreCase(searchTerm)
                .doOnSuccess(count -> log.info("Total count for username or email containing '{}': {}", searchTerm, count))
                .doOnError(e -> log.error("Error counting users by username or email {}: {}", searchTerm, e.getMessage(), e));
    }

    /**
     * Finds users created after a certain date with pagination, enriching each.
     *
     * @param date The cutoff date.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByCreatedAtAfter(LocalDateTime date, Pageable pageable) {
        log.info("Finding users created after {} with pagination: {}", date, pageable);
        return userRepository.findByCreatedAtAfter(date, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished finding users created after '{}' for page {} with size {}.", date, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users created after {}: {}", date, e.getMessage(), e));
    }

    /**
     * Counts users created after a certain date.
     *
     * @param date The cutoff date.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByCreatedAtAfter(LocalDateTime date) {
        log.info("Counting users created after {}", date);
        return userRepository.countByCreatedAtAfter(date)
                .doOnSuccess(count -> log.info("Total count for users created after {}: {}", date, count))
                .doOnError(e -> log.error("Error counting users created after {}: {}", date, e.getMessage(), e));
    }

    /**
     * Finds users with a specific shipping address (case-insensitive, contains) with pagination, enriching each.
     *
     * @param shippingAddress The shipping address to search for.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByShippingAddress(String shippingAddress, Pageable pageable) {
        log.info("Finding users by shipping address containing '{}' with pagination: {}", shippingAddress, pageable);
        return userRepository.findByShippingAddressContainingIgnoreCase(shippingAddress, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished finding users by shipping address containing '{}' for page {} with size {}.", shippingAddress, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by shipping address {}: {}", shippingAddress, e.getMessage(), e));
    }

    /**
     * Counts users with a specific shipping address (case-insensitive, contains).
     *
     * @param shippingAddress The shipping address to search for.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByShippingAddress(String shippingAddress) {
        log.info("Counting users by shipping address containing '{}'", shippingAddress);
        return userRepository.countByShippingAddressContainingIgnoreCase(shippingAddress)
                .doOnSuccess(count -> log.info("Total count for shipping address containing '{}': {}", shippingAddress, count))
                .doOnError(e -> log.error("Error counting users by shipping address {}: {}", shippingAddress, e.getMessage(), e));
    }

    /**
     * Checks if a user with the given email exists.
     *
     * @param email The email to check.
     * @return A Mono emitting true if the user exists, false otherwise.
     */
    public Mono<Boolean> existsByEmail(String email) {
        log.info("Checking if user exists by email: {}", email);
        return userRepository.existsByEmail(email)
                .doOnSuccess(exists -> log.info("User with email {} exists: {}", email, exists))
                .doOnError(e -> log.error("Error checking user existence by email {}: {}", email, e.getMessage(), e));
    }

    /**
     * Checks if a user with the given username exists.
     *
     * @param username The username to check.
     * @return A Mono emitting true if the user exists, false otherwise.
     */
    public Mono<Boolean> existsByUsername(String username) {
        log.info("Checking if user exists by username: {}", username);
        return userRepository.existsByUsername(username)
                .doOnSuccess(exists -> log.info("User with username {} exists: {}", username, exists))
                .doOnError(e -> log.error("Error checking user existence by username {}: {}", username, e.getMessage(), e));
    }
}
