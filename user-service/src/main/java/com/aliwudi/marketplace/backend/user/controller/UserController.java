package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.user.dto.UserDto; // Assuming you have a UserDto for public user data
import com.aliwudi.marketplace.backend.user.model.User; // Your User entity
import com.aliwudi.marketplace.backend.user.service.UserService; // The service layer for user operations
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;
import com.aliwudi.marketplace.backend.user.exception.ResourceNotFoundException; // Re-using or creating this for user not found

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.http.HttpStatus; // For internal clarity

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users") // A separate endpoint from /api/auth
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
                    // Attempt to cast to Long or parse String. Adjust based on your actual principal type.
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
                .flatMap(userId -> userService.getUserById(userId)) // Service returns Mono<User>
                .map(user -> (StandardResponseEntity) StandardResponseEntity.ok(
                        mapUserToUserDto(user), ApiResponseMessages.USER_DETAILS_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND))) // Should not happen if authenticated, but as fallback
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + e.getMessage())))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_USER_DETAILS + ": " + e.getMessage())));
    }

    /**
     * Get user details by ID (e.g., for admin users).
     * This method might require role-based authorization (e.g., hasRole('ADMIN')).
     */
    @GetMapping("/{id}")
    public Mono<StandardResponseEntity> getUserById(@PathVariable Long id) {
        // You might add an authorization check here based on the authenticated user's roles
        // if this endpoint is only for specific roles (e.g., ADMIN).

        return userService.getUserById(id) // Service returns Mono<User>
                .map(user -> (StandardResponseEntity) StandardResponseEntity.ok(
                        mapUserToUserDto(user), ApiResponseMessages.USER_DETAILS_RETRIEVED_SUCCESS))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND + id)))
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
    public Mono<StandardResponseEntity> updateMyUserDetails(@RequestBody UserDto userDto) {
        // Only allow specific fields to be updated by the user (e.g., email, not password or roles)
        // Ensure userDto does not contain sensitive fields like roles or password for this endpoint

        return getAuthenticatedUserId()
                .flatMap(userId -> userService.updateUser(userId, userDto)) // Service returns Mono<User>
                .map(updatedUser -> (StandardResponseEntity) StandardResponseEntity.ok(
                        mapUserToUserDto(updatedUser), ApiResponseMessages.USER_DETAILS_UPDATED_SUCCESS))
                .onErrorResume(ResourceNotFoundException.class, e -> // Should not happen for authenticated user, but as fallback
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + e.getMessage())))
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
                .flatMap(userId -> userService.deleteUser(userId)) // Service returns Mono<Void>
                .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.USER_DELETED_SUCCESS)))
                .onErrorResume(ResourceNotFoundException.class, e -> // User not found for deletion
                        Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(ApiResponseMessages.USER_NOT_FOUND + e.getMessage())))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.unauthorized(e.getMessage())))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_USER + ": " + e.getMessage())));
    }

    /**
     * Admin method to get all users.
     * This method will definitely require role-based authorization (e.g., hasRole('ADMIN')).
     */
    @GetMapping
    public Mono<StandardResponseEntity> getAllUsers() {
        // Implement authorization check for ADMIN role here
        // E.g., ReactiveSecurityContextHolder.getContext().map(sc -> sc.getAuthentication())
        //       .filter(auth -> auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
        //       .switchIfEmpty(Mono.error(new AccessDeniedException("Only ADMINs can view all users.")))
        //       .flatMap(auth -> userService.getAllUsers().collectList()) // Proceed if authorized

        return userService.getAllUsers() // Service returns Flux<User>
                .collectList() // Collect Flux into a List<User>
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
                .roles(user.getRoles() != null ?
                       user.getRoles().stream().map(role -> role.getName().name()).collect(Collectors.toSet()) :
                       null)
                .build();
    }
}