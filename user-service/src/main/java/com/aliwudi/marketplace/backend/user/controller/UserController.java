package com.aliwudi.marketplace.backend.user.controller;

// Static import for API path constants
import static com.aliwudi.marketplace.backend.common.constants.ApiConstants.*;

import com.aliwudi.marketplace.backend.user.service.UserService;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.common.exception.RoleNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.EmailSendingException; // New import
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages; // For consistent messages
import com.aliwudi.marketplace.backend.common.dto.UserProfileCreateRequest;
import com.aliwudi.marketplace.backend.common.enumeration.JwtClaims;
import com.aliwudi.marketplace.backend.user.auth.service.KeycloakSettings;
import com.aliwudi.marketplace.backend.user.dto.LoginRequest;
import com.aliwudi.marketplace.backend.user.dto.LogoutRequest;
// REMOVED: import com.aliwudi.marketplace.backend.user.auth.service.EmailVerificationService;
// REMOVED: import com.aliwudi.marketplace.backend.user.auth.service.IAdminService;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;
import static com.aliwudi.marketplace.backend.user.enumeration.KeycloakFormParams.*;

import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize; // For role-based authorization
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException; // For date parsing from request params
import java.util.Map;
import lombok.Data; // New import for DTOs
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // NEW IMPORT
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.reactive.function.BodyInserters; // NEW IMPORT
import org.springframework.web.reactive.function.client.WebClient;


/*
NOTE: In order to align with industry best practices we have removed
      authentication service implementation (jwt encoding, decoding e.t.c)
      from user-service microservice. Authentication is now solely done
      by authorization server (e.g keycloak)
 */
@Slf4j
@RestController
@RequestMapping(USER_CONTROLLER_BASE) // MODIFIED: Using constant for base path
@RequiredArgsConstructor // Generates a constructor for final fields
public class UserController {

    private final UserService userService;
    private final WebClient.Builder webClientBuilder; // Inject WebClient.Builder
    private WebClient webClient;
    private final ReactorClientHttpConnector connector;
    private final JwtDecoder jwtDecoder;

    private final KeycloakSettings kcSetting;

    // --- NEW: Request DTOs (Data Transfer Objects) for Email Verification ---
    @Data
    public static class VerificationRequest {

        private String authServerUserId; // Authorization Server ID of the user
        private String purpose;
        private String code;
    }

    @Data
    public static class ResendVerificationCodeRequest {

        private String authServerUserId;
        private String purpose;
    }
    // --- END NEW DTOs ---

    @jakarta.annotation.PostConstruct
    public void init() {
        this.webClient = webClientBuilder
                .clientConnector(connector)
                .baseUrl(kcSetting.getUrl())
                .build();
    }

    /**
     * Endpoint for user login using username/email and password. Authenticates
     * directly against Keycloak's token endpoint.
     *
     * @param loginRequest DTO containing username/email and password.
     * @return Mono<ResponseEntity<Map<String, Object>>> containing Keycloak
     * tokens.
     */
    @PostMapping(LOGIN)
    @ResponseStatus(HttpStatus.OK)
    public Mono<User> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Attempting login for user: {}", loginRequest.getUserIdentifier());

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", kcSetting.getUrl(), kcSetting.getRealm());

        // Build the form data for the token request
        BodyInserters.FormInserter<String> formData = BodyInserters
                .fromFormData(GRANT_TYPE.name(), PASSWORD.name())
                .with(CLIENT_ID.name(), kcSetting.getClientId())
                .with(USERNAME.name(), loginRequest.getUserIdentifier())
                .with(PASSWORD.name(), loginRequest.getPassword());

        // Only add client_secret if it's present (for confidential clients)
        if (kcSetting.getClientSecret() != null && !kcSetting.getClientSecret().isBlank()) {
            formData = formData.with("client_secret", kcSetting.getClientSecret());
        }

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), clientResponse
                        -> clientResponse.bodyToMono(Map.class)
                        .flatMap(errorBody -> {
                            String errorMessage = (String) errorBody.getOrDefault("error_description", "Authentication failed");
                            log.warn("Keycloak login failed for user {}: {}", loginRequest.getUserIdentifier(), errorMessage);
                            // You can create a more specific exception if needed
                            return Mono.error(new IllegalArgumentException("Login failed: " + errorMessage));
                        })
                )
                .onStatus(status -> status.is5xxServerError(), clientResponse
                        -> Mono.error(new RuntimeException("Keycloak server error during login for user: " + loginRequest.getUserIdentifier()))
                )
                .bodyToMono(Map.class) // Keycloak returns a map of tokens
                .flatMap(keycloakTokens -> {
                    log.info("Successfully authenticated user: {}", loginRequest.getUserIdentifier());

                    // Optionally, update the user's last login time in your backend DB
                    String accessToken = (String) keycloakTokens.get("access_token");

                    // Decode access token to get 'sub' (authId) for updating last login
                    // This requires JwtDecoder to be available in UserController or passed to UserService
                    // For now, let's just use the username to update last login if the User model has username
                    // or you can retrieve the authId from an ID token if present.
                    // Extract userId from access_token
                    try {
                        Jwt jwt = jwtDecoder.decode(accessToken); // Requires JwtDecoder in UserController or passed to UserService
                        Map<String, Object> claims = jwt.getClaims();
                        //String authId = jwt.getSubject();
                        String userId = (String) claims.get(JwtClaims.userId.name());
                        return Mono.just(Long.valueOf(userId));
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Could not decode access token to update last login: " + e.getMessage()));
                    }
                })
                .flatMap(userId -> userService.findById(userId))
                .flatMap(user -> {
                    log.info("User {} logging in. Updating last login time.", user.getId());
                    user.setLastLoginAt(LocalDateTime.now());
                    return userService.updateUserOnDB(user);
                })
                .doOnError(e -> log.error("Login process error for {}: {}", loginRequest.getUserIdentifier(), e.getMessage(), e));
    }

    @PostMapping(LOGOUT)
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> logout(@Valid @RequestBody LogoutRequest logoutRequest) {
        log.info("Attempting logout for user (refresh token revocation).");

        String revokeUrl = String.format("%s/realms/%s/protocol/openid-connect/revoke", kcSetting.getUrl(), kcSetting.getRealm());

        // Build the form data for the token revocation request
        BodyInserters.FormInserter<String> formData = BodyInserters
                .fromFormData(CLIENT_ID.name(), kcSetting.getClientId())
                .with(TOKEN.name(), logoutRequest.getRefreshToken())
                .with(TOKEN_TYPE_HINT.name(), REFRESH_TOKEN.name());

        // Only add client_secret if it's present (for confidential clients)
        if (kcSetting.getClientSecret() != null && !kcSetting.getClientSecret().isBlank()) {
            formData = formData.with("client_secret", kcSetting.getClientSecret());
        }

        return webClient.post()
                .uri(revokeUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> {
                    // Keycloak returns 200 even if the token is already invalid/not found,
                    // but it's good practice to handle potential errors.
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Keycloak token revocation failed. Status: {}, Response: {}", clientResponse.statusCode(), errorBody);
                                // Don't throw an error that prevents the user from logging out on client side,
                                // but log it for debugging.
                                return Mono.just(new RuntimeException("Token revocation failed: " + errorBody));
                            });
                })
                .bodyToMono(Void.class) // Keycloak returns an empty body on success (200 OK)
                .then(Mono.defer(() -> {
                    // After successful token revocation, you might want to record the logout in your DB
                    // You'll need the user ID to do this. This typically comes from the access token
                    // or a session managed by your backend. For simplicity here, we'll assume
                    // you can get the user ID from the refresh token if needed, or that
                    // recording logout is less critical than token invalidation.
                    // If you need the userId from the refresh token, you'd need to decode it here.
                    // For now, let's just log and then call a generic recordLogout.

                    // Decode the refresh token to get the userId for recording logout
                    try {
                        Jwt jwt = jwtDecoder.decode(logoutRequest.getRefreshToken());
                        Map<String, Object> claims = jwt.getClaims();
                        String userIdString = (String) claims.get(JwtClaims.userId.name());
                        if (userIdString != null) {
                            Long userId = Long.valueOf(userIdString);
                            return userService.findById(userId)
                                    .flatMap(user -> {
                                        log.info("User {} logging in. Updating last login time.", user.getId());
                                        user.setLastLogoutAt(LocalDateTime.now());
                                        return userService.updateUserOnDB(user);
                                    }).thenReturn(true);

                        } else {
                            log.warn("Could not extract userId from refresh token for logout recording.");
                            return Mono.just(false);
                        }
                    } catch (Exception e) {
                        log.error("Failed to decode refresh token or record logout: {}", e.getMessage(), e);
                        return Mono.just(false);
                    }
                }));
    }

    /**
     * Creates a new user profile in the backend database and registers the user
     * in Authorization Server. This method now implements the "backend-first"
     * hybrid registration approach. This endpoint is typically called by the
     * frontend (mobile/web) for user self-registration. It now also initiates
     * email verification immediately after successful user creation.
     *
     * @param request The UserProfileCreateRequest containing user details
     * including password.
     * @return A Mono emitting the created User object.
     * @throws DuplicateResourceException if a user with the given email or
     * phoneNumber already exists.
     * @throws RoleNotFoundException if a specified role does not exist.
     * @throws IllegalArgumentException if input is invalid.
     * @throws EmailSendingException if the initial verification email fails to
     * send.
     */
    @PostMapping(USER_PROFILES_CREATE)
    @ResponseStatus(HttpStatus.CREATED)
    // IMPORTANT: For public registration, this endpoint should NOT have @PreAuthorize.
    // If it's an internal service-to-service endpoint, secure it with client_credentials.
    // Assuming this is the public registration entry point for now.
    public Mono<User> createUser(@Valid @RequestBody UserProfileCreateRequest request) {

        // Basic validation for critical fields (can be enhanced with @Validated groups if desired)
        if (request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return Mono.error(new IllegalArgumentException(ApiResponseMessages.INVALID_USER_CREATION_REQUEST));
        }

        // The UserService.createUser now handles the entire backend DB save, Authorization Server registration,
        // email verification initiation, and rollback logic.
        return userService.createUser(request);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to verify an email using an OTP. This endpoint is typically
     * called by the frontend after the user enters the OTP.
     *
     * @param request The VerificationRequest containing authServerUserId and
     * the OTP code.
     * @return A Mono emitting a success/failure message.
     */
    @PostMapping(SMS_VERIFICATION_VERIFY_OTP) // NEW Endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> verifySms(@RequestBody VerificationRequest request) {
        return verifyPhoneNumber(request);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to verify an email using an OTP. This endpoint is typically
     * called by the frontend after the user enters the OTP.
     *
     * @param request The VerificationRequest containing authServerUserId and
     * the OTP code.
     * @return A Mono emitting a success/failure message.
     */
    @PostMapping(PHONE_CALL_VERIFICATION_VERIFY_OTP) // NEW Endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> verifyPhoneCall(@RequestBody VerificationRequest request) {
        return verifyPhoneNumber(request);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    public Mono<Boolean> verifyPhoneNumber(@RequestBody VerificationRequest request) {
        if (request.getAuthServerUserId() == null || request.getAuthServerUserId().isBlank()) {
            return Mono.error(new IllegalArgumentException("Verification Code is required."));
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            return Mono.error(new IllegalArgumentException("Verification Code is required."));
        }
        if (request.getPurpose() == null || request.getPurpose().isBlank()) {
            return Mono.error(new IllegalArgumentException("Verification purpose is required."));
        }
        return userService.verifyPhoneOtp(request.getAuthServerUserId(), request.getCode(), request.getPurpose());
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to verify an email using an OTP. This endpoint is typically
     * called by the frontend after the user enters the OTP.
     *
     * @param request The VerificationRequest containing authServerUserId and
     * the OTP code.
     * @return A Mono emitting a success/failure message.
     */
    @PostMapping(EMAIL_VERIFICATION_VERIFY_OTP) // NEW Endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> verifyEmail(@RequestBody VerificationRequest request) {
        if (request.getAuthServerUserId() == null || request.getAuthServerUserId().isBlank()) {
            return Mono.error(new IllegalArgumentException("Verification Code is required."));
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            return Mono.error(new IllegalArgumentException("Verification Code is required."));
        }
        if (request.getPurpose() == null || request.getPurpose().isBlank()) {
            return Mono.error(new IllegalArgumentException("Verification purpose is required."));
        }
        return userService.verifyEmailOtp(request.getAuthServerUserId(), request.getCode(), request.getPurpose());
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to resend an email verification code (OTP). This endpoint is
     * typically called by the frontend if the user didn't receive the first
     * OTP.
     *
     * @param request The ResendVerificationCodeRequest containing the
     * authServerUserId.
     * @return A Mono emitting a success/failure message.
     */
    @PostMapping(EMAIL_VERIFICATION_RESEND_CODE) // NEW Endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> resendEmailVerificationCode(@RequestBody ResendVerificationCodeRequest request) {
        if (request.getAuthServerUserId() == null || request.getAuthServerUserId().isBlank()) {
            return Mono.error(new IllegalArgumentException("Auth Server User ID is required."));
        }

        return userService.resendEmailVerificationCode(request.getAuthServerUserId());
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to resend an SMS verification code (OTP). This endpoint is
     * typically called by the frontend if the user didn't receive the first
     * OTP.
     *
     * @param request The ResendVerificationCodeRequest containing the
     * authServerUserId.
     * @return A Mono emitting a success/failure message.
     */
    @PostMapping(SMS_VERIFICATION_RESEND_CODE) // NEW Endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> resendSmsVerificationCode(@RequestBody ResendVerificationCodeRequest request) {
        if (request.getAuthServerUserId() == null || request.getAuthServerUserId().isBlank()) {
            return Mono.error(new IllegalArgumentException("Auth Server User ID is required."));
        }

        return userService.resendSmsVerificationCode(request.getAuthServerUserId());
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to resend an phone call verification code (OTP). This endpoint
     * is typically called by the frontend if the user didn't receive the first
     * OTP.
     *
     * @param request The ResendVerificationCodeRequest containing the
     * authServerUserId.
     * @return A Mono emitting a success/failure message.
     */
    @PostMapping(PHONE_CALL_VERIFICATION_RESEND_CODE) // NEW Endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> resendPhoneCallVerificationCode(@RequestBody ResendVerificationCodeRequest request) {
        if (request.getAuthServerUserId() == null || request.getAuthServerUserId().isBlank()) {
            return Mono.error(new IllegalArgumentException("Auth Server User ID is required."));
        }

        return userService.resendPhoneCallVerificationCode(request.getAuthServerUserId());
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Retrieves a user by their Authorization Server authorization ID.
     *
     * @param authId The Authorization Server authorization ID of the user to
     * retrieve.
     * @return A Mono emitting the User object.
     * @throws ResourceNotFoundException if the user is not found.
     * @throws IllegalArgumentException if authId is invalid.
     */
    @GetMapping(USER_BY_AUTH_ID)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')") // Example authorization
    public Mono<User> getUserByAuthId(@PathVariable String authId) {
        log.info("Fetching user with Auth ID: {}", authId);
        if (authId == null || authId.isBlank()) {
            return Mono.error(new IllegalArgumentException(ApiResponseMessages.INVALID_AUTH_ID));
        }
        return userService.findByAuthId(authId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_AUTH_ID + authId)));
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Updates an existing user profile.
     *
     * @param id The ID of the user to update.
     * @param userRequest The UserRequest containing updated user details.
     * @return A Mono emitting the updated User object.
     * @throws ResourceNotFoundException if the user is not found.
     * @throws DuplicateResourceException if the updated email already exists.
     * @throws IllegalArgumentException if input is invalid.
     */
    @PutMapping(USER_PROFILES_UPDATE)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'USER') and #id == authentication.principal.claims['sub']") // User can update own profile, Admin can update any
    public Mono<User> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest userRequest) { // Changed to String
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        // Basic check for at least one field to update
        if (userRequest.getFirstName() == null && userRequest.getLastName() == null
                && userRequest.getEmail() == null && userRequest.getPhoneNumber() == null
                && userRequest.getShippingAddress() == null && userRequest.getRoles() == null) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_UPDATE_REQUEST);
        }
        return userService.updateUser(id, userRequest);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a user by their ID. Accessible only by 'admin'.
     *
     * @param id The ID of the user to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @DeleteMapping(USER_PROFILES_DELETE) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
    public Mono<Void> deleteUser(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        return userService.deleteUser(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a user by their Auth ID. Accessible only by 'admin'
     * The method is typically called by the authorization server (e.g Keycloak)
     * for the purpose of rollback in the case where an error occurred during
     * user creation to avoid data inconsistency arising when an error occurs
     * while creating the corresponding user on the micro service after creation
     * in the authorization server or any such related errors that can cause
     * inconsistency * @param authId The auth ID (ID at the authorization server
     * e.g Keycloak) of the user to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @DeleteMapping(USER_PROFILES_DELETE_ROLLBACK) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('" + ROLE_USER_PROFILE_SYNC + "')") // MODIFIED: Using constant for role
    public Mono<Void> deleteUserByAuthId(@PathVariable String authId) {
        if (authId == null || authId.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        return userService.deleteUserByAuthId(authId);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a user by their ID. Accessible by 'admin' or the
     * 'user' themselves.
     *
     * @param id The ID of the user to retrieve.
     * @return A Mono emitting the User.
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @GetMapping(USER_GET_BY_ID) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "') or hasRole('" + ROLE_USER + "')") // MODIFIED: Using constants for roles
    public Mono<User> getUserById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID);
        }
        return userService.getUserById(id);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a user by their phoneNumber. Accessible by 'admin'
     * or for specific public lookups (e.g., phoneNumber availability check).
     *
     * @param phoneNumber The phoneNumber of the user.
     * @return A Mono emitting the User.
     * @throws IllegalArgumentException if phoneNumber is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @GetMapping(USER_GET_BY_PHONE_NUMBER) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
    public Mono<User> getUserByPhoneNumber(@PathVariable String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PHONE_NUMBER);
        }
        return userService.getUserByPhoneNumber(phoneNumber);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a user by their email. Accessible by 'admin' only
     * due to privacy concerns.
     *
     * @param email The email of the user.
     * @return A Mono emitting the User.
     * @throws IllegalArgumentException if email is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @GetMapping(USER_GET_BY_EMAIL) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
    public Mono<User> getUserByEmail(@PathVariable String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_EMAIL);
        }
        return userService.getUserByEmail(email);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve all users with pagination. Accessible by 'admin'
     * only.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of User records.
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    @GetMapping(USER_ADMIN_ALL) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
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
     * Endpoint to count all users. Accessible by 'admin' only.
     *
     * @return A Mono emitting the total count of users.
     */
    @GetMapping(USER_ADMIN_COUNT) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
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
     * @throws IllegalArgumentException if first name or pagination parameters
     * are invalid.
     */
    @GetMapping(USER_ADMIN_BY_FIRST_NAME) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
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
     * Counts users by first name (case-insensitive, contains). Accessible by
     * 'admin'.
     *
     * @param firstName The first name to search for.
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if first name is invalid.
     */
    @GetMapping(USER_ADMIN_COUNT_BY_FIRST_NAME) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
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
     * @throws IllegalArgumentException if last name or pagination parameters
     * are invalid.
     */
    @GetMapping(USER_ADMIN_BY_LAST_NAME) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
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
     * Counts users by last name (case-insensitive, contains). Accessible by
     * 'admin'.
     *
     * @param lastName The last name to search for.
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if last name is invalid.
     */
    @GetMapping(USER_ADMIN_COUNT_BY_LAST_NAME) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
    public Mono<Long> countUsersByLastName(@RequestParam String lastName) {
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_LAST_NAME);
        }
        return userService.countUsersByLastName(lastName);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds users by phoneNumber or email (case-insensitive, contains) with
     * pagination. Accessible by 'admin'.
     *
     * @param searchTerm The search term for phoneNumber or email.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting matching users.
     * @throws IllegalArgumentException if search term or pagination parameters
     * are invalid.
     */
    @GetMapping(USER_ADMIN_BY_PHONE_NUMBER_OR_EMAIL) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
    public Flux<User> getUsersByPhoneNumberOrEmail(
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
        return userService.getUsersByPhoneNumberOrEmail(searchTerm, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Counts users by phoneNumber or email (case-insensitive, contains).
     * Accessible by 'admin'.
     *
     * @param searchTerm The search term for phoneNumber or email.
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if search term is invalid.
     */
    @GetMapping(USER_ADMIN_COUNT_BY_PHONE_NUMBER_OR_EMAIL) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
    public Mono<Long> countUsersByPhoneNumberOrEmail(@RequestParam String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SEARCH_TERM);
        }
        return userService.countUsersByPhoneNumberOrEmail(searchTerm);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Finds users created after a certain date with pagination. Accessible by
     * 'admin'.
     *
     * @param date The cutoff date (ISO 8601 format:WriteHeader-MM-ddTHH:mm:ss).
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting matching users.
     * @throws IllegalArgumentException if date format or pagination parameters
     * are invalid.
     */
    @GetMapping(USER_ADMIN_BY_CREATED_AT_AFTER) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
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
     * Counts users created after a certain date. Accessible by 'admin'.
     *
     * @param date The cutoff date (ISO 8601 format:WriteHeader-MM-ddTHH:mm:ss).
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if date format is invalid.
     */
    @GetMapping(USER_ADMIN_COUNT_BY_CREATED_AT_AFTER) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
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
     * Finds users with a specific shipping address (case-insensitive, contains)
     * with pagination. Accessible by 'admin'.
     *
     * @param shippingAddress The shipping address to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux emitting matching users.
     * @throws IllegalArgumentException if shipping address or pagination
     * parameters are invalid.
     */
    @GetMapping(USER_ADMIN_BY_SHIPPING_ADDRESS) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
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
     * Counts users with a specific shipping address (case-insensitive,
     * contains). Accessible by 'admin'.
     *
     * @param shippingAddress The shipping address to search for.
     * @return A Mono emitting the count of matching users.
     * @throws IllegalArgumentException if shipping address is invalid.
     */
    @GetMapping(USER_ADMIN_COUNT_BY_SHIPPING_ADDRESS) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + ROLE_ADMIN + "')") // MODIFIED: Using constant for role
    public Mono<Long> countUsersByShippingAddress(@RequestParam String shippingAddress) {
        if (shippingAddress == null || shippingAddress.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_SHIPPING_ADDRESS);
        }
        return userService.countUsersByShippingAddress(shippingAddress);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Checks if a user with the given authorization id exists.
     *
     * @param authId The authId to check.
     * @return A Mono emitting true if the user exists, false otherwise
     * (Boolean).
     * @throws IllegalArgumentException if email is invalid.
     */
    @GetMapping(USER_EXISTS_BY_AUTH_ID) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsByAuthId(@RequestParam String authId) {
        if (authId == null || authId.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_AUTHORIZATION_ID);
        }
        return userService.existsByAuthId(authId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Checks if a user with the given user id exists.
     *
     * @param userId The userId to check.
     * @return A Mono emitting true if the user exists, false otherwise
     * (Boolean).
     * @throws IllegalArgumentException if email is invalid.
     */
    @GetMapping(USER_EXISTS_BY_USER_ID) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsByUserId(@RequestParam Long userId) {
        if (userId == null || userId < 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_ID); // Changed from INVALID_EMAIL
        }
        return userService.existsByUserId(userId);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Checks if a user with the given email exists. Can be used for
     * registration forms to check email availability.
     *
     * @param email The email to check.
     * @return A Mono emitting true if the user exists, false otherwise
     * (Boolean).
     * @throws IllegalArgumentException if email is invalid.
     */
    @GetMapping(USER_EXISTS_BY_EMAIL) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsByEmail(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_EMAIL); // Changed from INVALID_USER_ID
        }
        return userService.existsByEmail(email);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Checks if a user with the given phoneNumber exists. Can be used for
     * registration forms to check phoneNumber availability.
     *
     * @param phoneNumber The phoneNumber to check.
     * @return A Mono emitting true if the user exists, false otherwise
     * (Boolean).
     * @throws IllegalArgumentException if phoneNumber is invalid.
     */
    @GetMapping(USER_EXISTS_BY_PHONE_NUMBER) // MODIFIED: Using constant for endpoint
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsByPhoneNumber(@RequestParam String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PHONE_NUMBER);
        }
        return userService.existsByPhoneNumber(phoneNumber);
        // Errors are handled by GlobalExceptionHandler.
    }
}
