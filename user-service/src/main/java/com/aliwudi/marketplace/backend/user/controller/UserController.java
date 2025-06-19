package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.user.service.UserService;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.common.exception.RoleNotFoundException;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages; // For consistent messages
import com.aliwudi.marketplace.backend.user.dto.UserProfileCreateRequest;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;
import com.aliwudi.marketplace.backend.user.validation.CreateUserValidation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize; // For role-based authorization
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException; // For date parsing from request params
import org.springframework.validation.annotation.Validated;

/*
NOTE: In order to align with industry best practices we have removed
      authentication service implementation (jwt encoding, decoding e.t.c)
      from user-service microservice. Authentication is now solely done 
      by authorization server (e.g keycloak)
*/

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor // Generates a constructor for final fields
public class UserController {

    private final UserService userService;

    /**
     * Endpoint to create a new user.
     * Accessible by 'admin' or can be exposed for public registration.
     *
     * @param request The DTO containing user creation data.
     * @return A Mono emitting the created User.
     * @throws IllegalArgumentException if input validation fails.
     * @throws DuplicateResourceException if username or email already exist.
     * @throws RoleNotFoundException if any specified role does not exist.
     */
    @PostMapping("/profiles/create")
    @PreAuthorize("hasRole('user-profile-sync')") // Ensure this method is protected by the role
    @ResponseStatus(HttpStatus.CREATED)
    // Here is where it's used: We tell Spring to validate using the 'CreateUserValidation' group.
    // This will activate the @Size constraint on the 'password' field that belongs to this group.
    public Mono<User> createUser(@Validated(CreateUserValidation.class) @RequestBody UserProfileCreateRequest request) {
        // Basic validation at controller level for required fields
        // Note: With @Validated(CreateUserValidation.class) and @NotBlank on username/email
        // this manual check for username/email.isBlank() might become redundant,
        // but it's kept for explicit demonstration of controller-level checks for critical fields.
        if (request.getUsername() == null || request.getUsername().isBlank() ||
            request.getEmail() == null || request.getEmail().isBlank()) { // Password check might be removed if @Validated covers it
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_CREATION_REQUEST);
        }
        // If the password was valid for the CreateUserValidation group, it means it's not blank
        // and meets the size requirement, so the manual userRequest.getPassword().isBlank() check
        // for password might become less necessary or could be simplified.

        return userService.createUser(request);
        // Exceptions (DuplicateResourceException, RoleNotFoundException, IllegalArgumentException,
        // WebExchangeBindException from validation failures)
        // are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to update an existing user's information.
     * Accessible by 'admin' or the 'user' themselves.
     *
     * @param id The ID of the user to update.
     * @param userRequest The DTO containing updated user data.
     * @return A Mono emitting the updated User.
     * @throws IllegalArgumentException if user ID is invalid or update data is insufficient.
     * @throws ResourceNotFoundException if the user is not found.
     * @throws DuplicateResourceException if updated username or email already exist.
     * @throws RoleNotFoundException if any specified role does not exist during role update.
     */
    @PostMapping("/profiles/update/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('user-profile-sync')") // Ensure this method is protected by the role
    // Here, we use just @Valid (or @Validated without a group).
    // This means only default validation constraints (those without a 'groups' attribute, or with 'groups=Default.class')
    // will be applied. The @Size constraint on 'password' in UserRequest will NOT be active here,
    // allowing you to update other user fields without providing a password.
    public Mono<User> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest userRequest) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        // Basic check for at least one field to update
        if (userRequest.getFirstName() == null && userRequest.getLastName() == null &&
            userRequest.getEmail() == null && userRequest.getUsername() == null &&
            userRequest.getShippingAddress() == null && userRequest.getRoleNames() == null) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_UPDATE_REQUEST);
        }
        return userService.updateUser(id, userRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }


    /**
     * Endpoint to delete a user by their ID.
     * Accessible only by 'admin'.
     *
     * @param id The ID of the user to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @DeleteMapping("/profiles/delete/{id}") // Admin endpoint
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('admin')")
    public Mono<Void> deleteUser(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        return userService.deleteUser(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }
    
    /**
     * Endpoint to delete a user by their Auth ID. Accessible
     * only by 'admin' The method is typically called by 
     * the authorization server (e.g Keycloak) for the purpose
     * of rollback in the case where an error occurred during
     * user creation to avoid data inconsistency arising when
     * an error occurs while creating the corresponding user on 
     * the micro service after creation in the authorization server
     * or any such related errors that can cause inconsistency
     * 
     *
     * @param authId The auth ID (ID at the authorization server e.g Keycloak)
     *               of the user to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @DeleteMapping("/profiles/delete-to-rollback/{authId}") 
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('user-profile-sync')") // Ensure this method is protected by the role
    public Mono<Void> deleteUserByAuthId(@PathVariable String authId) {
        if (authId == null || authId.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        return userService.deleteUserByAuthId(authId);
        // Exceptions are handled by GlobalExceptionHandler.
    }
    

    /**
     * Endpoint to retrieve a user by their ID.
     * Accessible by 'admin' or the 'user' themselves.
     *
     * @param id The ID of the user to retrieve.
     * @return A Mono emitting the User.
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin') or hasRole('user')") // Example authorization
    public Mono<User> getUserById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        return userService.getUserById(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a user by their username.
     * Accessible by 'admin' or for specific public lookups (e.g., username availability check).
     *
     * @param username The username of the user.
     * @return A Mono emitting the User.
     * @throws IllegalArgumentException if username is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @GetMapping("/byUsername/{username}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')") // Typically admin only, or if public profile viewing is allowed
    public Mono<User> getUserByUsername(@PathVariable String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USERNAME);
        }
        return userService.getUserByUsername(username);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a user by their email.
     * Accessible by 'admin' only due to privacy concerns.
     *
     * @param email The email of the user.
     * @return A Mono emitting the User.
     * @throws IllegalArgumentException if email is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @GetMapping("/byEmail/{email}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<User> getUserByEmail(@PathVariable String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_EMAIL);
        }
        return userService.getUserByEmail(email);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all users with pagination.
     * Accessible by 'admin' only.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of User records.
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    @GetMapping("/admin/all")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<User> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.getAllUsers(pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count all users.
     * Accessible by 'admin' only.
     *
     * @return A Mono emitting the total count of users.
     */
    @GetMapping("/admin/count")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countAllUsers() {
        return userService.countAllUsers();
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds users by first name (case-insensitive, contains) with pagination.
     * Accessible by 'admin'.
     *
     * @param firstName The first name to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting matching users.
     * @throws IllegalArgumentException if first name or pagination parameters are invalid.
     */
    @GetMapping("/admin/byFirstName")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<User> getUsersByFirstName(
            @RequestParam String firstName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (firstName == null || firstName.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_FIRST_NAME + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.getUsersByFirstName(firstName, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts users by first name (case-insensitive, contains).
     * Accessible by 'admin'.
     *
     * @param firstName The first name to search for.
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if first name is invalid.
     */
    @GetMapping("/admin/countByFirstName")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countUsersByFirstName(@RequestParam String firstName) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_FIRST_NAME);
        }
        return userService.countUsersByFirstName(firstName);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds users by last name (case-insensitive, contains) with pagination.
     * Accessible by 'admin'.
     *
     * @param lastName The last name to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting matching users.
     * @throws IllegalArgumentException if last name or pagination parameters are invalid.
     */
    @GetMapping("/admin/byLastName")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<User> getUsersByLastName(
            @RequestParam String lastName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (lastName == null || lastName.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_LAST_NAME + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.getUsersByLastName(lastName, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts users by last name (case-insensitive, contains).
     * Accessible by 'admin'.
     *
     * @param lastName The last name to search for.
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if last name is invalid.
     */
    @GetMapping("/admin/countByLastName")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countUsersByLastName(@RequestParam String lastName) {
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_LAST_NAME);
        }
        return userService.countUsersByLastName(lastName);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds users by username or email (case-insensitive, contains) with pagination.
     * Accessible by 'admin'.
     *
     * @param searchTerm The search term for username or email.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting matching users.
     * @throws IllegalArgumentException if search term or pagination parameters are invalid.
     */
    @GetMapping("/admin/byUsernameOrEmail")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<User> getUsersByUsernameOrEmail(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (searchTerm == null || searchTerm.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.getUsersByUsernameOrEmail(searchTerm, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts users by username or email (case-insensitive, contains).
     * Accessible by 'admin'.
     *
     * @param searchTerm The search term for username or email.
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if search term is invalid.
     */
    @GetMapping("/admin/countByUsernameOrEmail")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countUsersByUsernameOrEmail(@RequestParam String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return userService.countUsersByUsernameOrEmail(searchTerm);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds users created after a certain date with pagination.
     * Accessible by 'admin'.
     *
     * @param date The cutoff date (ISO 8601 format:WriteHeader-MM-ddTHH:mm:ss).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting matching users.
     * @throws IllegalArgumentException if date format or pagination parameters are invalid.
     */
    @GetMapping("/admin/byCreatedAtAfter")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<User> getUsersByCreatedAtAfter(
            @RequestParam String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (date == null || date.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DATE_FORMAT + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        LocalDateTime dateTime;
        try {
            dateTime = LocalDateTime.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.getUsersByCreatedAtAfter(dateTime, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts users created after a certain date.
     * Accessible by 'admin'.
     *
     * @param date The cutoff date (ISO 8601 format:WriteHeader-MM-ddTHH:mm:ss).
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if date format is invalid.
     */
    @GetMapping("/admin/countByCreatedAtAfter")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countUsersByCreatedAtAfter(@RequestParam String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_DATE_FORMAT);
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(date);
            return userService.countUsersByCreatedAtAfter(dateTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");
        }
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds users with a specific shipping address (case-insensitive, contains) with pagination.
     * Accessible by 'admin'.
     *
     * @param shippingAddress The shipping address to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting matching users.
     * @throws IllegalArgumentException if shipping address or pagination parameters are invalid.
     */
    @GetMapping("/admin/byShippingAddress")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<User> getUsersByShippingAddress(
            @RequestParam String shippingAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (shippingAddress == null || shippingAddress.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SHIPPING_ADDRESS + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userService.getUsersByShippingAddress(shippingAddress, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts users with a specific shipping address (case-insensitive, contains).
     * Accessible by 'admin'.
     *
     * @param shippingAddress The shipping address to search for.
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if shipping address is invalid.
     */
    @GetMapping("/admin/countByShippingAddress")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countUsersByShippingAddress(@RequestParam String shippingAddress) {
        if (shippingAddress == null || shippingAddress.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SHIPPING_ADDRESS);
        }
        return userService.countUsersByShippingAddress(shippingAddress);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Checks if a user with the given email exists.
     * Can be used for registration forms to check email availability.
     *
     * @param email The email to check.
     * @return A Mono emitting true if the user exists, false otherwise (Boolean).
     * @throws IllegalArgumentException if email is invalid.
     */
    @GetMapping("/existsByEmail")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsByEmail(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_EMAIL);
        }
        return userService.existsByEmail(email);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Checks if a user with the given username exists.
     * Can be used for registration forms to check username availability.
     *
     * @param username The username to check.
     * @return A Mono emitting true if the user exists, false otherwise (Boolean).
     * @throws IllegalArgumentException if username is invalid.
     */
    @GetMapping("/existsByUsername")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsByUsername(@RequestParam String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USERNAME);
        }
        return userService.existsByUsername(username);
        // Errors are handled by GlobalExceptionHandler.
    }
}
