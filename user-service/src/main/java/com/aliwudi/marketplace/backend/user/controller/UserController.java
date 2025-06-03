package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.common.dto.UserDto;
import com.aliwudi.marketplace.backend.user.model.User;
import com.aliwudi.marketplace.backend.user.service.UserService;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.user.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.user.dto.UserUpdateRequest; // Import for update requests
import com.aliwudi.marketplace.backend.common.enumeration.ERole; // Import ERole for role search

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Import Flux
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize; // For role-based authorization
import org.springframework.data.domain.Pageable; // For pagination
import org.springframework.data.domain.PageRequest; // For creating Pageable instances
import org.springframework.data.domain.Sort; // For sorting

import java.time.LocalDateTime; // For date queries
import java.time.format.DateTimeParseException; // For parsing dates
import java.util.List;
import java.util.stream.Collectors;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.role.ERole;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Helper method to get the authenticated user's ID from the reactive SecurityContextHolder.
     * This ID is propagated by the API Gateway.
     * @return A Mono emitting the authenticated user's ID.
     * @throws IllegalStateException if the user is not authenticated or ID cannot be retrieved.
     */
    private Mono<Long> getAuthenticatedUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        return Mono.error(new IllegalStateException(ApiResponseMessages.UNAUTHENTICATED_USER));
                    }
                    if (authentication.getPrincipal() instanceof Long) {
                        return Mono.just((Long) authentication.getPrincipal());
                    } else if (authentication.getPrincipal() instanceof String) {
                        try {
                            return Mono.just(Long.parseLong((String) authentication.getPrincipal()));
                        } catch (NumberFormatException e) {
                            return Mono.error(new IllegalStateException(ApiResponseMessages.INVALID_USER_ID_FORMAT, e));
                        }
                    }
                    return Mono.error(new IllegalStateException(ApiResponseMessages.INVALID_USER_ID));
                })
                .switchIfEmpty(Mono.error(new IllegalStateException(ApiResponseMessages.SECURITY_CONTEXT_NOT_FOUND)));
    }

    /**
     * Get details of the currently authenticated user.
     * Accessible by the user themselves.
     */
    @GetMapping("/me")
    public Mono<StandardResponseEntity> getMyUserDetails() {
        return getAuthenticatedUserId()
                .flatMap(userId -> userService.getUserById(userId))
                .map(user -> (StandardResponseEntity) StandardResponseEntity.ok(mapUserToUserDto(user), ApiResponseMessages.USER_DETAILS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + e.getMessage())))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_USER_DETAILS + ": " + e.getMessage())));
    }

    /**
     * Get user details by ID (e.g., for admin users).
     * This method requires role-based authorization (e.g., hasRole('ADMIN')).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')") // Only ADMINs can access this endpoint
    public Mono<StandardResponseEntity> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> (StandardResponseEntity) StandardResponseEntity.ok(mapUserToUserDto(user), ApiResponseMessages.USER_DETAILS_RETRIEVED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_USER_DETAILS + ": " + e.getMessage())));
    }

    /**
     * Update details of the currently authenticated user.
     * Allows user to update their own profile (e.g., email, non-password fields).
     */
    @PutMapping("/me")
    public Mono<StandardResponseEntity> updateMyUserDetails(@RequestBody UserUpdateRequest userUpdateRequest) {
        return getAuthenticatedUserId()
                .flatMap(userId -> userService.updateUser(userId, userUpdateRequest))
                .map(updatedUser -> (StandardResponseEntity) StandardResponseEntity.ok(mapUserToUserDto(updatedUser), ApiResponseMessages.USER_DETAILS_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + e.getMessage())))
                .onErrorResume(IllegalArgumentException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPDATING_USER_DETAILS + ": " + e.getMessage())));
    }

    /**
     * Delete the currently authenticated user's account.
     * This should be a highly protected endpoint.
     */
    @DeleteMapping("/me")
    public Mono<StandardResponseEntity> deleteMyAccount() {
        return getAuthenticatedUserId()
                .flatMap(userId -> userService.deleteUser(userId))
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.USER_DELETED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e -> Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + e.getMessage())))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_USER + ": " + e.getMessage())));
    }

    /**
     * Admin method to get users.
     * This method requires role-based authorization (e.g., hasRole('ADMIN')).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')") // Only ADMINs can access this endpoint
    public Mono<StandardResponseEntity> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // Create a Pageable object
        Pageable pageable = PageRequest.of(page, size);

        return userService.getAllUsers(pageable) // Modify your service to accept Pageable
                .collectList() // Collect the Flux into a List for mapping
                .map(users -> users.stream()
                        .map(this::mapUserToUserDto)
                        .collect(Collectors.toList()))
                .map(userDtos -> (StandardResponseEntity) StandardResponseEntity.ok(userDtos, ApiResponseMessages.USERS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ALL_USERS + ": " + e.getMessage())));
    }
    // Helper method to map User entity to UserDto for public exposure
    private UserDto mapUserToUserDto(User user) {
        if (user == null) {
            return null;
        }
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles()
                       )
                .build();
    }

    // --- NEW: Controller Endpoints for all UserRepository methods ---

    /**
     * Endpoint to find a user by their username.
     *
     * @param username The username to search for.
     * @return A Mono emitting StandardResponseEntity with the UserDto if found.
     */
    @GetMapping("/byUsername/{username}")
    public Mono<StandardResponseEntity> getUserByUsername(@PathVariable String username) {
        return userService.findByUsername(username)
                .map(user -> StandardResponseEntity.ok(mapUserToUserDto(user), ApiResponseMessages.USER_DETAILS_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + " with username: " + username)))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_USER_DETAILS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find a user by their email.
     *
     * @param email The email to search for.
     * @return A Mono emitting StandardResponseEntity with the UserDto if found.
     */
    @GetMapping("/byEmail/{email}")
    public Mono<StandardResponseEntity> getUserByEmail(@PathVariable String email) {
        return userService.findByEmail(email)
                .map(user -> StandardResponseEntity.ok(mapUserToUserDto(user), ApiResponseMessages.USER_DETAILS_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + " with email: " + email)))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_USER_DETAILS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find users by first name, with pagination.
     *
     * @param firstName The first name to search for (case-insensitive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of UserDto records.
     */
    @GetMapping("/byFirstName/{firstName}")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserDto> getUsersByFirstNameContainingIgnoreCase(
            @PathVariable String firstName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.findUsersByFirstNameContainingIgnoreCase(firstName, pageable)
                .map(this::mapUserToUserDto);
    }

    /**
     * Endpoint to count users by first name.
     *
     * @param firstName The first name to count (case-insensitive).
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byFirstName/{firstName}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countUsersByFirstNameContainingIgnoreCase(@PathVariable String firstName) {
        return userService.countUsersByFirstNameContainingIgnoreCase(firstName)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.USER_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_USERS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find users by last name, with pagination.
     *
     * @param lastName The last name to search for (case-insensitive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of UserDto records.
     */
    @GetMapping("/byLastName/{lastName}")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserDto> getUsersByLastNameContainingIgnoreCase(
            @PathVariable String lastName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.findUsersByLastNameContainingIgnoreCase(lastName, pageable)
                .map(this::mapUserToUserDto);
    }

    /**
     * Endpoint to count users by last name.
     *
     * @param lastName The last name to count (case-insensitive).
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byLastName/{lastName}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countUsersByLastNameContainingIgnoreCase(@PathVariable String lastName) {
        return userService.countUsersByLastNameContainingIgnoreCase(lastName)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.USER_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_USERS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find users by username or email, with pagination.
     *
     * @param searchTerm The search term (username or email, case-insensitive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of UserDto records.
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserDto> getUsersByUsernameOrEmailContainingIgnoreCase(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.findUsersByUsernameOrEmailContainingIgnoreCase(searchTerm, pageable)
                .map(this::mapUserToUserDto);
    }

    /**
     * Endpoint to count users by username or email.
     *
     * @param searchTerm The search term (username or email, case-insensitive).
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/search")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countUsersByUsernameOrEmailContainingIgnoreCase(@RequestParam String searchTerm) {
        return userService.countUsersByUsernameOrEmailContainingIgnoreCase(searchTerm)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.USER_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_USERS + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find users created after a certain date, with pagination.
     *
     * @param date The cutoff date (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of UserDto records.
     */
    @GetMapping("/createdAtAfter")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserDto> getUsersByCreatedAtAfter(
            @RequestParam String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(date);
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            return userService.findUsersByCreatedAtAfter(dateTime, pageable)
                    .map(this::mapUserToUserDto);
        } catch (DateTimeParseException e) {
            return Flux.error(new IllegalArgumentException("Invalid date format. Please use ISO 8601 format: YYYY-MM-ddTHH:mm:ss."));
        }
    }

    /**
     * Endpoint to count users created after a certain date.
     *
     * @param date The cutoff date (ISO 8601 format: YYYY-MM-ddTHH:mm:ss).
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/createdAtAfter")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countUsersByCreatedAtAfter(@RequestParam String date) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(date);
            return userService.countUsersByCreatedAtAfter(dateTime)
                    .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.USER_COUNT_RETRIEVED_SUCCESS))
                    .onErrorResume(Exception.class, e ->
                            Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_USERS + ": " + e.getMessage())));
        } catch (DateTimeParseException e) {
            return Mono.just(StandardResponseEntity.badRequest("Invalid date format. Please use ISO 8601 format: YYYY-MM-ddTHH:mm:ss."));
        }
    }

    /**
     * Endpoint to find users with a specific shipping address, with pagination.
     *
     * @param shippingAddress The shipping address to search for (case-insensitive).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of UserDto records.
     */
    @GetMapping("/byShippingAddress")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserDto> getUsersByShippingAddressContainingIgnoreCase(
            @RequestParam String shippingAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.findUsersByShippingAddressContainingIgnoreCase(shippingAddress, pageable)
                .map(this::mapUserToUserDto);
    }

    /**
     * Endpoint to count users with a specific shipping address.
     *
     * @param shippingAddress The shipping address to count (case-insensitive).
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byShippingAddress")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countUsersByShippingAddressContainingIgnoreCase(@RequestParam String shippingAddress) {
        return userService.countUsersByShippingAddressContainingIgnoreCase(shippingAddress)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.USER_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_USERS + ": " + e.getMessage())));
    }

    // --- NEW: Controller Endpoints for all RoleRepository methods ---

    /**
     * Endpoint to find a role by its name.
     *
     * @param roleName The name of the role (e.g., "ROLE_USER", "ROLE_ADMIN").
     * @return A Mono emitting StandardResponseEntity with the Role.
     */
    @GetMapping("/roles/byName/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> getRoleByName(@PathVariable String roleName) {
        ERole eRole;
        try {
            eRole = ERole.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ROLE_PROVIDED + roleName));
        }
        return userService.findRoleByName(eRole)
                .map(role -> StandardResponseEntity.ok(role, ApiResponseMessages.ROLE_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just(StandardResponseEntity.notFound(ApiResponseMessages.ROLE_NOT_FOUND + roleName)))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ROLE + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find roles with names containing a specific string (case-insensitive), with pagination.
     *
     * @param name The string to search for in role names.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Role records.
     */
    @GetMapping("/roles/search")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<com.aliwudi.marketplace.backend.user.model.Role> searchRolesByNameContainingIgnoreCase(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.findRolesByNameContainingIgnoreCase(name, pageable);
    }

    /**
     * Endpoint to count roles with names containing a specific string (case-insensitive).
     *
     * @param name The string to count in role names.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/roles/count/search")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countRolesByNameContainingIgnoreCase(@RequestParam String name) {
        return userService.countRolesByNameContainingIgnoreCase(name)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ROLE_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ROLES + ": " + e.getMessage())));
    }

    /**
     * Endpoint to find all roles with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of Role records.
     */
    @GetMapping("/roles/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<com.aliwudi.marketplace.backend.user.model.Role> getAllRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.findAllRoles(pageable);
    }

    /**
     * Endpoint to count all roles.
     *
     * @return A Mono emitting StandardResponseEntity with the total count.
     */
    @GetMapping("/roles/count/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countAllRoles() {
        return userService.countAllRoles()
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.ROLE_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ROLES + ": " + e.getMessage())));
    }
}