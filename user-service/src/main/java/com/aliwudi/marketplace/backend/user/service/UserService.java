package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import com.aliwudi.marketplace.backend.user.repository.RoleRepository;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.model.Role;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.common.exception.RoleNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.dto.UserProfileCreateRequest;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;
import com.aliwudi.marketplace.backend.user.auth.service.IAdminService; // MODIFIED: Import IAdminService
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;


/**
 * Service class for managing user-related business logic.
 * Handles operations like creating, retrieving, updating, and deleting users.
 * Now implements the "backend-first" hybrid registration flow with generic Authorization Server integration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional // Apply transactional behavior at the service layer
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final IAdminService iAdminService; // MODIFIED: Inject IAdminService

    /**
     * Helper method to map User entity to User DTO for public exposure. This
     * method enriches the User object with its associated Role details. Assumes
     * that the User model has a 'Set<Role> roles' field that can be set.
     */
    private Mono<User> prepareDto(User user) {
        if (user == null) {
            return Mono.empty();
        }

        if (user.getRoles() == null || (user.getRoles().isEmpty() && user.getId() != null)) {
            return roleRepository.findRolesByUserId(user.getId())
                    .collectList()
                    .doOnNext(roles -> user.setRoles(new HashSet<>(roles)))
                    .then(Mono.just(user))
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch roles for user {}: {}", user.getId(), e.getMessage());
                        user.setRoles(new HashSet<>());
                        return Mono.just(user);
                    });
        }
        return Mono.just(user);
    }

    Mono<Set<Role>> getRolesOfAuthenticatedUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> {
                    Authentication authentication = context.getAuthentication();
                    Set<String> roles = authentication.getAuthorities()
                            .stream().map(f -> {
                                System.out.println(f.getAuthority());
                                return f.getAuthority();
                            })
                            .filter(str -> str.startsWith("ROLE_"))
                            .map(roleStr -> roleStr.replaceFirst("ROLE_", ""))
                            .collect(Collectors.toSet());

                    return Flux.fromIterable(roles)
                            .flatMap(roleName
                                    -> roleRepository.findByName(roleName)
                                    .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND + ": " + roleName)))
                            )
                            .collectList();
                })
                .flatMap(roleListMono -> roleListMono)
                .map(roleList -> roleList
                .stream()
                .collect(Collectors.toSet()));

    }

    /**
     * Creates a new user in the backend database and then registers them in the Authorization Server.
     * This method implements the "backend-first" hybrid registration approach.
     * If Authorization Server registration fails, the user is rolled back (deleted) from the backend database.
     *
     * @param request The DTO containing user creation data, including password.
     * @return A Mono emitting the created User object, updated with Authorization Server's authId.
     * @throws DuplicateResourceException if a user with the same email or username already exists in backend DB or Authorization Server.
     * @throws RoleNotFoundException if a specified role does not exist.
     * @throws RuntimeException if Authorization Server registration fails for other reasons.
     */
    @Transactional
    public Mono<User> createUser(UserProfileCreateRequest request) {
        log.debug("Attempting to create user in backend database from UserProfileCreateRequest: {}", request.getUsername());

        return Mono.zip(
                userRepository.existsByEmail(request.getEmail()),
                userRepository.existsByUsername(request.getUsername())
        ).flatMap(tuple -> {
            boolean emailExists = tuple.getT1();
            boolean usernameExists = tuple.getT2();

            if (emailExists) {
                log.warn("User creation failed in backend: Email '{}' already exists.", request.getEmail());
                return Mono.error(new DuplicateResourceException(ApiResponseMessages.EMAIL_ALREADY_EXISTS));
            }
            if (usernameExists) {
                log.warn("User creation failed in backend: Username '{}' already exists.", request.getUsername());
                return Mono.error(new DuplicateResourceException(ApiResponseMessages.USERNAME_ALREADY_EXISTS));
            }

            // Create User entity for backend DB
            User newUser = new User();
            newUser.setUsername(request.getUsername());
            newUser.setEmail(request.getEmail());
            newUser.setFirstName(request.getFirstName());
            newUser.setLastName(request.getLastName());
            newUser.setPhoneNumber(request.getPhoneNumber());
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            newUser.setEnabled(true);

            // Default role assignment for new users (e.g., 'USER')
            return roleRepository.findByName("USER")
                    .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND + "USER")))
                    .flatMap(defaultRole -> {
                        Set<Role> roles = new HashSet<>();
                        roles.add(defaultRole);
                        newUser.setRoles(roles);
                        return userRepository.save(newUser); // Save to DB, ID will be generated
                    })
                    .flatMap(persistedUser -> {
                        Long userId = persistedUser.getId();
                        log.info("User '{}' created successfully in backend DB with internal ID: {}. Proceeding to Authorization Server registration.", persistedUser.getUsername(), userId); // Generic log

                        // 2. Register user in Authorization Server via Admin API
                        return iAdminService.createUserInAuthServer( // MODIFIED: Call generic method
                                        request.getUsername(),
                                        request.getEmail(),
                                        request.getPassword(),
                                        userId,
                                        request.getFirstName(),
                                        request.getLastName()
                                )
                                .flatMap(authServerAuthId -> { // MODIFIED: Generic variable name
                                    // 3. Update backend user with Authorization Server's authId
                                    persistedUser.setAuthId(authServerAuthId); // MODIFIED: Generic variable name
                                    log.info("User registered in Authorization Server with Auth ID: {}. Updating backend user.", authServerAuthId); // Generic log
                                    return userRepository.save(persistedUser); // Update the user in backend DB with Auth Server Auth ID
                                })
                                .onErrorResume(e -> {
                                    // 4. If Authorization Server registration fails, rollback (delete) user from backend DB
                                    log.error("Failed to register user in Authorization Server for internal ID {}. Initiating rollback of backend user. Error: {}", // Generic log
                                            userId, e.getMessage(), e);
                                    return userRepository.delete(persistedUser)
                                            .then(Mono.error(new RuntimeException(ApiResponseMessages.USER_REGISTRATION_FAILED + ": " + e.getMessage(), e)));
                                });
                    })
                    .flatMap(this::prepareDto)
                    .doOnSuccess(u -> log.debug("User created successfully with ID: {}", u.getId()))
                    .doOnError(e -> log.error("Error creating user {}: {}", request.getUsername(), e.getMessage(), e));
    });
                }            



    /**
     * Finds a user by their internal database ID.
     *
     * @param id The internal ID of the user.
     * @return A Mono emitting the User object, or empty if not found.
     */
    public Mono<User> findById(Long id) {
        log.debug("Finding user by ID: {}", id);
        return userRepository.findById(id)
                .doOnSuccess(user -> {
                    if (user != null) log.debug("Found user by ID: {}", id);
                    else log.debug("User with ID {} not found.", id);
                })
                .doOnError(e -> log.error("Error finding user by ID {}: {}", id, e.getMessage(), e));
    }

    /**
     * Finds a user by their Authorization Server authorization ID.
     *
     * @param authId The Authorization Server authorization ID.
     * @return A Mono emitting the User object, or empty if not found.
     */
    public Mono<User> findByAuthId(String authId) {
        log.debug("Finding user by authorization ID: {}", authId);
        return userRepository.findByAuthId(authId)
                .doOnSuccess(user -> {
                    if (user != null) log.debug("Found user by authorization ID: {}", authId);
                    else log.debug("User with authorization ID {} not found.", authId);
                })
                .doOnError(e -> log.error("Error finding user by authorization ID {}: {}", authId, e.getMessage(), e));
    }

    /**
     * Retrieves all users with pagination.
     *
     * @param pageable Pagination information.
     * @return A Flux emitting User objects.
     */
    public Flux<User> findAll(Pageable pageable) {
        log.debug("Fetching all users with pageable: {}", pageable);
        return userRepository.findAllBy(pageable)
                .doOnError(e -> log.error("Error fetching all users: {}", e.getMessage(), e));
    }

    /**
     * Updates an existing user's details.
     *
     * @param id The internal ID of the user to update.
     * @param userRequest The DTO containing updated user information.
     * @return A Mono emitting the updated User object.
     * @throws ResourceNotFoundException if the user is not found.
     * @throws DuplicateResourceException if the updated email or username already exists for another user.
     */
    @Transactional
    public Mono<User> updateUser(Long id, UserRequest userRequest) {
        log.debug("Updating user with ID: {}", id);
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_ID + id)))
                .flatMap(existingUser -> {
                    // Check for duplicate username if changed
                    Mono<Void> usernameCheck = Mono.empty();
                    if (userRequest.getUsername() != null && !userRequest.getUsername().isBlank()
                            && !existingUser.getUsername().equalsIgnoreCase(userRequest.getUsername())) {
                        usernameCheck = userRepository.existsByUsername(userRequest.getUsername())
                                .flatMap(exists -> {
                                    if (exists) {
                                        log.warn("Attempt to update username to an existing one: {}", userRequest.getUsername());
                                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.USERNAME_ALREADY_EXISTS));
                                    }
                                    return Mono.empty();
                                });
                    }

                    // Check for duplicate email if changed
                    Mono<Void> emailCheck = Mono.empty();
                    if (userRequest.getEmail() != null && !userRequest.getEmail().isBlank()
                            && !existingUser.getEmail().equalsIgnoreCase(userRequest.getEmail())) {
                        emailCheck = userRepository.existsByEmail(userRequest.getEmail())
                                .flatMap(exists -> {
                                    if (exists) {
                                        log.warn("Attempt to update email to an existing one: {}", userRequest.getEmail());
                                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.EMAIL_ALREADY_EXISTS));
                                    }
                                    return Mono.empty();
                                });
                    }

                    // Combine checks
                    return Mono.when(usernameCheck, emailCheck)
                            .thenReturn(existingUser);
                })
                .flatMap(existingUser -> {
                    // Apply updates
                    if (userRequest.getAuthId() != null && !userRequest.getAuthId().isBlank()) {
                        existingUser.setAuthId(userRequest.getAuthId());
                    }

                    if (userRequest.getUsername() != null && !userRequest.getUsername().isBlank()) {
                        existingUser.setUsername(userRequest.getUsername());
                    }
                    if (userRequest.getEmail() != null && !userRequest.getEmail().isBlank()) {
                        existingUser.setEmail(userRequest.getEmail());
                    }
                    if (userRequest.getPhoneNumber() != null && !userRequest.getPhoneNumber().isBlank()) {
                        existingUser.setPhoneNumber(userRequest.getPhoneNumber());
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

                    // Handle roles if provided in UserRequest, otherwise keep existing roles
                    if (userRequest.getRoleNames() != null && !userRequest.getRoleNames().isEmpty()) {
                        Set<Mono<Role>> roleMonos = userRequest.getRoleNames().stream()
                                .map(roleName -> roleRepository.findByName(roleName)
                                        .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND+" : " + roleName))))
                                .collect(Collectors.toSet());

                        return Flux.fromIterable(roleMonos)
                                .flatMap(mono -> mono)
                                .collect(Collectors.toSet())
                                .flatMap(newRoles -> {
                                    existingUser.setRoles(newRoles);
                                    return userRepository.save(existingUser);
                                });
                    } else {
                        return userRepository.save(existingUser);
                    }
                })
                .flatMap(this::prepareDto)
                .doOnSuccess(u -> log.debug("User updated successfully with ID: {}", u.getId()))
                .doOnError(e -> log.error("Error updating user with ID {}: {}", id, e.getMessage(), e));
    }

    /**
     * Updates an existing user's Authorization Server Auth ID.
     * This method is used after successful Authorization Server registration.
     *
     * @param user The User object with the Authorization Server Auth ID to update.
     * @return A Mono emitting the updated User object.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @Transactional
    public Mono<User> updateUser(User user) {
        log.debug("Updating user's Authorization Server Auth ID for user: {}", user.getId()); // Generic log
        return userRepository.findById(user.getId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + user.getId())))
                .flatMap(existingUser -> {
                    existingUser.setAuthId(user.getAuthId());
                    existingUser.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(existingUser);
                })
                .doOnSuccess(updatedUser -> log.info("User {} Authorization Server Auth ID updated to {}.", updatedUser.getId(), updatedUser.getAuthId())) // Generic log
                .doOnError(e -> log.error("Error updating user {} Authorization Server Auth ID: {}", user.getId(), e.getMessage(), e)); // Generic log
    }


    /**
     * Deletes a user by their ID. This operation is transactional.
     *
     * @param id The ID of the user to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @Transactional
    public Mono<Void> deleteUser(Long id) {
        log.debug("Attempting to delete user with ID: {}", id);
        return userRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("User deleted successfully with ID: {}", id))
                .doOnError(e -> log.error("Error deleting user {}: {}", id, e.getMessage(), e));
    }

    /**
     * Deletes a user by their Authorization Server authorization ID from both backend DB and Authorization Server.
     * This method is typically called by an admin or for cleanup purposes.
     *
     * @param authId The auth ID (ID at the authorization server e.g Authorization Server)
     * of the user to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @Transactional
    public Mono<Void> deleteUserByAuthId(String authId) {
        log.debug("Attempting to delete user with Auth ID: {}", authId);
       return userRepository.deleteByAuthId(authId) // Delete from backend DB first
                .then(iAdminService.deleteUserFromAuthServer(authId)) // MODIFIED: Call generic method
                .doOnSuccess(v -> log.debug("User deleted successfully with Auth ID: {}", authId))
                .doOnError(e -> log.error("Error deleting user {}: {}", authId, e.getMessage(), e));
    }

    /**
     * Retrieves a user by their ID, enriching them.
     *
     * @param id The ID of the user to retrieve.
     * @return A Mono emitting the User if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Mono<User> getUserById(Long id) {
        log.debug("Retrieving user by ID: {}", id);
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
                .flatMap(this::prepareDto)
                .doOnSuccess(user -> log.debug("User retrieved successfully: {}", user.getId()))
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
        log.debug("Retrieving user by username: {}", username);
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_USERNAME + username)))
                .flatMap(this::prepareDto)
                .doOnSuccess(user -> log.debug("User retrieved successfully by username: {}", user.getUsername()))
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
        log.debug("Retrieving user by email: {}", email);
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_EMAIL + email)))
                .flatMap(this::prepareDto)
                .doOnSuccess(user -> log.debug("User retrieved successfully by email: {}", user.getEmail()))
                .doOnError(e -> log.error("Error retrieving user by email {}: {}", email, e.getMessage(), e));
    }

    /**
     * Retrieves all users with pagination, enriching each.
     *
     * @param pageable Pagination information.
     * @return A Flux emitting all users (enriched).
     */
    public Flux<User> getAllUsers(Pageable pageable) {
        log.debug("Retrieving all users with pagination: {}", pageable);
        return userRepository.findAllBy(pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.debug("Finished retrieving all users for page {} with size {}.", pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving all users: {}", e.getMessage(), e));
    }

    /**
     * Counts all users.
     *
     * @return A Mono emitting the total count of users.
     */
    public Mono<Long> countAllUsers() {
        log.debug("Counting all users.");
        return userRepository.count()
                .doOnSuccess(count -> log.debug("Total user count: {}", count))
                .doOnError(e -> log.error("Error counting all users: {}", e.getMessage(), e));
    }

    /**
     * Finds users by first name (case-insensitive, contains) with pagination,
     * enriching each.
     *
     * @param firstName The first name to search for.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByFirstName(String firstName, Pageable pageable) {
        log.debug("Finding users by first name '{}' with pagination: {}", firstName, pageable);
        return userRepository.findByFirstNameContainingIgnoreCase(firstName, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.debug("Finished finding users by first name '{}' for page {} with size {}.", firstName, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by first name {}: {}", firstName, e.getMessage(), e));
    }

    /**
     * Counts users by first name (case-insensitive, contains).
     *
     * @param firstName The first name to search for.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByFirstName(String firstName) {
        log.debug("Counting users by first name '{}'", firstName);
        return userRepository.countByFirstNameContainingIgnoreCase(firstName)
                .doOnSuccess(count -> log.debug("Total count for first name '{}': {}", firstName, count))
                .doOnError(e -> log.error("Error counting users by first name {}: {}", firstName, e.getMessage(), e));
    }

    /**
     * Finds users by last name (case-insensitive, contains) with pagination,
     * enriching each.
     *
     * @param lastName The last name to search for.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByLastName(String lastName, Pageable pageable) {
        log.debug("Finding users by last name '{}' with pagination: {}", lastName, pageable);
        return userRepository.findByLastNameContainingIgnoreCase(lastName, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.debug("Finished finding users by last name '{}' for page {} with size {}.", lastName, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by last name {}: {}", lastName, e.getMessage(), e));
    }

    /**
     * Counts users by last name (case-insensitive, contains).
     *
     * @param lastName The last name to search for.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByLastName(String lastName) {
        log.debug("Counting users by last name '{}'", lastName);
        return userRepository.countByLastNameContainingIgnoreCase(lastName)
                .doOnSuccess(count -> log.debug("Total count for last name '{}': {}", lastName, count))
                .doOnError(e -> log.error("Error counting users by last name {}: {}", lastName, e.getMessage(), e));
    }

    /**
     * Finds users by username or email (case-insensitive, contains) with
     * pagination, enriching each.
     *
     * @param searchTerm The search term for username or email.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByUsernameOrEmail(String searchTerm, Pageable pageable) {
        log.debug("Finding users by username or email containing '{}' with pagination: {}", searchTerm, pageable);
        return userRepository.findByUsernameOrEmailContainingIgnoreCase(searchTerm, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.debug("Finished finding users by username or email containing '{}' for page {} with size {}.", searchTerm, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by username or email {}: {}", searchTerm, e.getMessage(), e));
    }

    /**
     * Counts users by username or email (case-insensitive, contains).
     *
     * @param searchTerm The search term for username or email.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByUsernameOrEmail(String searchTerm) {
        log.debug("Counting users by username or email containing '{}'", searchTerm);
        return userRepository.countByUsernameOrEmailContainingIgnoreCase(searchTerm)
                .doOnSuccess(count -> log.debug("Total count for username or email containing '{}': {}", searchTerm, count))
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
        log.debug("Finding users created after {} with pagination: {}", date, pageable);
        return userRepository.findByCreatedAtAfter(date, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.debug("Finished finding users created after '{}' for page {} with size {}.", date, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users created after {}: {}", date, e.getMessage(), e));
    }

    /**
     * Counts users created after a certain date.
     *
     * @param date The cutoff date.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByCreatedAtAfter(LocalDateTime date) {
        log.debug("Counting users created after {}", date);
        return userRepository.countByCreatedAtAfter(date)
                .doOnSuccess(count -> log.debug("Total count for users created after {}: {}", date, count))
                .doOnError(e -> log.error("Error counting users created after {}: {}", date, e.getMessage(), e));
    }

    /**
     * Finds users with a specific shipping address (case-insensitive, contains)
     * with pagination, enriching each.
     *
     * @param shippingAddress The shipping address to search for.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByShippingAddress(String shippingAddress, Pageable pageable) {
        log.debug("Finding users by shipping address containing '{}' with pagination: {}", shippingAddress, pageable);
        return userRepository.findByShippingAddressContainingIgnoreCase(shippingAddress, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.debug("Finished finding users by shipping address containing '{}' for page {} with size {}.", shippingAddress, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by shipping address {}: {}", shippingAddress, e.getMessage(), e));
    }

    /**
     * Counts users with a specific shipping address (case-insensitive,
     * contains).
     *
     * @param shippingAddress The shipping address to search for.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByShippingAddress(String shippingAddress) {
        log.debug("Counting users by shipping address containing '{}'", shippingAddress);
        return userRepository.countByShippingAddressContainingIgnoreCase(shippingAddress)
                .doOnSuccess(count -> log.debug("Total count for shipping address containing '{}': {}", shippingAddress, count))
                .doOnError(e -> log.error("Error counting users by shipping address {}: {}", shippingAddress, e.getMessage(), e));
    }

    /**
     * Checks if a user with the given user id exists.
     *
     * @param userId The userId to check.
     * @return A Mono emitting true if the user exists, false otherwise.
     */
    public Mono<Boolean> existsByUserId(Long userId) {
        log.debug("Checking if user exists by user id: {}", userId);
        return userRepository.existsById(userId)
                .doOnSuccess(exists -> log.debug("User with id {} exists: {}", userId, exists))
                .doOnError(e -> log.error("Error checking user existence by id {}: {}", userId, e.getMessage(), e));
    }

    /**
     * Checks if a user with the given authorization id exists.
     *
     * @param authId The authId to check.
     * @return A Mono emitting true if the user exists, false otherwise.
     */
    public Mono<Boolean> existsByAuthId(String authId) {
        log.debug("Checking if user exists by authorization id: {}", authId);
        return userRepository.existsByAuthId(authId)
                .doOnSuccess(exists -> log.debug("User with authorization id {} exists: {}", authId, exists))
                .doOnError(e -> log.error("Error checking user existence by authorization id {}: {}", authId, e.getMessage(), e));
    }

    /**
     * Checks if a user with the given email exists.
     *
     * @param email The email to check.
     * @return A Mono emitting true if the user exists, false otherwise.
     */
    public Mono<Boolean> existsByEmail(String email) {
        log.debug("Checking if user exists by email: {}", email);
        return userRepository.existsByEmail(email)
                .doOnSuccess(exists -> log.debug("User with email {} exists: {}", email, exists))
                .doOnError(e -> log.error("Error checking user existence by email {}: {}", email, e.getMessage(), e));
    }

    /**
     * Checks if a user with the given username exists.
     *
     * @param username The username to check.
     * @return A Mono emitting true if the user exists, false otherwise.
     */
    public Mono<Boolean> existsByUsername(String username) {
        log.debug("Checking if user exists by username: {}", username);
        return userRepository.existsByUsername(username)
                .doOnSuccess(exists -> log.debug("User with username {} exists: {}", username, exists))
                .doOnError(e -> log.error("Error checking user existence by username {}: {}", username, e.getMessage(), e));
    }

}
