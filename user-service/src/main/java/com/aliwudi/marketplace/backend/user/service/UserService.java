package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import com.aliwudi.marketplace.backend.user.repository.RoleRepository;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.model.Role;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.common.exception.RoleNotFoundException; // Assuming this custom exception exists or using ResourceNotFoundException

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // For transactional operations
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.dto.UserProfileCreateRequest;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

@Service
@RequiredArgsConstructor // Generates a constructor for final fields (UserRepository, RoleRepository, PasswordEncoder)
@Slf4j // Enables Lombok's logging
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AdminService adminService; // Inject for Authorizatio server Admin API calls

    // IMPORTANT: This prepareDto method is for enriching the User model.
    // It is placed here as per your instruction to move it to an appropriate location
    // and is not modified. It enriches the User object with its associated roles.
    /**
     * Helper method to map User entity to User DTO for public exposure. This
     * method enriches the User object with its associated Role details. Assumes
     * that the User model has a 'Set<Role> roles' field that can be set.
     */
    private Mono<User> prepareDto(User user) {
        if (user == null) {
            return Mono.empty();
        }

        // Fetch roles for the user if not already set or partially loaded
        // This assumes a Many-to-Many relationship between User and Role,
        // and that User has a method like 'setRoles'.
        // Also assuming RoleRepository has a method to find roles by user ID if roles are not directly loaded.
        if (user.getRoles() == null || (user.getRoles().isEmpty() && user.getId() != null)) {
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

    Mono<Set<Role>> getRolesOfAuthenticatedUser() {
        //Get the roles in the authentication as provided by the authorization
        //server in the jwt token
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> {
                    Authentication authenticatoion = context.getAuthentication();
                    Set<String> roles = authenticatoion.getAuthorities()
                            .stream().map(f -> {
                                System.out.println(f.getAuthority());
                                return f.getAuthority();
                            })
                            .filter(str -> str.startsWith("ROLE_"))//filter only the roles. skip the scopes and any others
                            .map(roleStr -> roleStr.replaceFirst("ROLE_", ""))//remove the ROLE_ prefix
                            .collect(Collectors.toSet());

                    return Flux.fromIterable(roles)
                            .flatMap(roleName
                                    -> roleRepository.findByName(roleName)
                                    //throw error if the role in the authentication object does not exist on the server (database)
                                    //it means the role is invalid because the role in authorizatio server
                                    //must match with what we have on this server database
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
     * Creates a new user in the system. Performs checks for duplicate username
     * and email, hashes the password, and assigns default roles if none are
     * specified, or specific roles if provided. This operation is
     * transactional.
     *
     * @param request The DTO containing user creation data.
     * @return A Mono emitting the created User (enriched with roles).
     * @throws DuplicateResourceException if username or email already exist.
     * @throws RoleNotFoundException if any specified role does not exist.
     */
    @Transactional
    public Mono<User> createUser(UserProfileCreateRequest request) {
        log.debug("Attempting to create user with username: {} and email: {}", request.getUsername(), request.getEmail());

        // Check for duplicate username and email concurrently
        Mono<Boolean> usernameExists = userRepository.existsByUsername(request.getUsername());
        Mono<Boolean> emailExists = userRepository.existsByEmail(request.getEmail());

        return Mono.zip(usernameExists, emailExists)
                .flatMap(tuple -> {
                    boolean isUsernameTaken = tuple.getT1();
                    boolean isEmailInUse = tuple.getT2();

                    if (isUsernameTaken) {
                        log.warn("Username already taken: {}", request.getUsername());
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.USERNAME_ALREADY_EXISTS));
                    }
                    if (isEmailInUse) {
                        log.warn("Email already in use: {}", request.getEmail());
                        return Mono.error(new DuplicateResourceException(ApiResponseMessages.EMAIL_ALREADY_EXISTS));
                    }

                    // Build user object
                    User user = User.builder()
                            .authId(request.getAuthId())
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .phoneNumber(request.getPhoneNumber())
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .enabled(true)
                            .build();

                    //Get the roles from the authentication object
                    return getRolesOfAuthenticatedUser()// This is Mono<Set<Role>>                            
                            .flatMap(roles -> { // 'roles' here is the Set<Role> obtained from rolesMono
                                user.setRoles(roles); // Set roles on the user object
                                return userRepository.save(user); // Save the user
                            })
                            .flatMap(savedUser -> {
                                // CRUCIAL STEP: After saving and getting the internal Long ID,
                                // update Keycloak user attribute. This is chained reactively.
                                String internalUserId = savedUser.getId().toString();
                                return adminService
                                        .updateUserAttribute(savedUser.getAuthId(),
                                                "user_id", internalUserId)
                                        .thenReturn(savedUser); // Return the savedUser after Keycloak update
                            })
                            .flatMap(this::prepareDto) // Enrich the saved user
                            .doOnSuccess(u -> log.debug("User created successfully with ID: {}", u.getId()))
                            .doOnError(e -> log.error("Error creating user {}: {}", request.getUsername(), e.getMessage(), e));
                    // Exceptions are handled by GlobalExceptionHandler.
                });
    }

    /**
     * Updates an existing user's information. Handles partial updates and
     * ensures email/username uniqueness if changed. This operation is
     * transactional.
     *
     * @param id The ID of the user to update.
     * @param userRequest The DTO containing updated user data.
     * @return A Mono emitting the updated User (enriched).
     * @throws ResourceNotFoundException if the user is not found.
     * @throws DuplicateResourceException if updated username or email already
     * exist.
     * @throws RoleNotFoundException if any specified role does not exist during
     * role update.
     */
    @Transactional
    public Mono<User> updateUser(Long id, UserRequest userRequest) {
        log.debug("Attempting to update user with ID: {}", id);
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
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
                            .thenReturn(existingUser); // Pass the existing user along
                })
                .flatMap(existingUser -> {
                    // Apply updates
                    if (userRequest.getAuthId() != null && !userRequest.getAuthId().isBlank()) {
                        existingUser.setAuthId(userRequest.getAuthId()); //although it is permanent!
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

                    return getRolesOfAuthenticatedUser()
                            .flatMap(roles -> { // 'roles' here is the Set<Role> obtained from rolesMono
                                existingUser.setRoles(roles); // Set roles on the user object
                                return userRepository.save(existingUser); // Save the user
                            });
                })
                .flatMap(this::prepareDto) // Enrich the updated user
                .doOnSuccess(u -> log.debug("User updated successfully with ID: {}", u.getId()))
                .doOnError(e -> log.error("Error updating user {}: {}", id, e.getMessage(), e));
        // Exceptions are handled by GlobalExceptionHandler.
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
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Deletes a user by Auth ID. This operation is transactional.
     *
     * @param authId The ID of the user to delete.
     * @return A Mono<Void> indicating completion.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @Transactional
    public Mono<Void> deleteUserByAuthId(String authId) {
        log.debug("Attempting to delete user with Auth ID: {}", authId);
       return userRepository.deleteByAuthId(authId)
                .then(adminService.deleteUser(authId)) // CRUCIAL: Also delete user from AS for consistency                
                .doOnSuccess(v -> log.debug("User deleted successfully with Auth ID: {}", authId))
                .doOnError(e -> log.error("Error deleting user {}: {}", authId, e.getMessage(), e));                
        // Exceptions are handled by GlobalExceptionHandler.
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
                .flatMap(this::prepareDto) // Enrich the user
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
                .flatMap(this::prepareDto) // Enrich the user
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
                .flatMap(this::prepareDto) // Enrich the user
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
                .flatMap(this::prepareDto) // Enrich each user
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
