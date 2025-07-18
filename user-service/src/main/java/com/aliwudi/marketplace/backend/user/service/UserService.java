package com.aliwudi.marketplace.backend.user.service;

import static com.aliwudi.marketplace.backend.common.constants.ApiConstants.LOGIN;
import static com.aliwudi.marketplace.backend.common.constants.ApiConstants.USER_CONTROLLER_BASE;
import static com.aliwudi.marketplace.backend.common.constants.EventType.USER_REGISTER;
import com.aliwudi.marketplace.backend.common.constants.IdentifierType;
import com.aliwudi.marketplace.backend.user.repository.UserRepository;
import com.aliwudi.marketplace.backend.user.repository.RoleRepository;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.model.Role;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.common.exception.RoleNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.EmailSendingException; // New import
import com.aliwudi.marketplace.backend.common.exception.OtpValidationException; // New import
import com.aliwudi.marketplace.backend.common.exception.UserNotFoundException; // New import for  user not found

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
import com.aliwudi.marketplace.backend.common.exception.InvalidUserDataException;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;
import com.aliwudi.marketplace.backend.user.auth.service.IAdminService;
import static com.aliwudi.marketplace.backend.user.enumeration.AuthServerAttribute.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

/**
 * Service class for managing user-related business logic. Handles operations
 * like creating, retrieving, updating, and deleting users. Now implements the
 * "backend-first" hybrid registration flow with generic Authorization Server
 * integration and integrated email verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional // Apply transactional behavior at the service layer
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final IAdminService iAdminService; // For Authorization Server Admin API interactions
    private final OtpService otpService; // NEW: Inject OtpService
    private final NotificationEventPublisherService notificationEventPublisherService; // Already injected

    // OTP validity for email verification (e.g., 5 minutes)
    private static final Duration EMAIL_OTP_VALIDITY = Duration.ofMinutes(5);
    private static final Duration SMS_OTP_VALIDITY = Duration.ofMinutes(5);
    private static final Duration PHONE_CALL_OTP_VALIDITY = Duration.ofMinutes(5);

    @Value("${app.host}")
    private String appHost;

    private String getLoginPath() {
        return appHost + USER_CONTROLLER_BASE + LOGIN;
    }

    
    /**
     * Initiates the password reset process by publishing an event to the
     * notification service. This method generates a temporary token and
     * publishes an event for email delivery.
     *
     * @param identifier The email address of the user requesting password
     * reset.
     * @return Mono<Void> indicating the event has been published.
     * @throws ResourceNotFoundException if no user found with the given email.
     */
    public Mono<Void> initiatePasswordReset(String identifier) {
        log.info("Initiating password reset for user identifier: {}", identifier);
        return userRepository.findByEmail(identifier)
                .switchIfEmpty(userRepository.findByPhoneNumber(identifier))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND)))
                .flatMap(user -> {
                    // In a real application, you would generate a secure, time-limited password reset token here.
                    // This token would be stored in your database/cache associated with the user.
                    String resetToken = "TEMP_RESET_TOKEN_" + user.getId() + "_" + System.currentTimeMillis(); // Placeholder
                    String resetLink = "https://your-app.com/reset-password?token=" + resetToken; // Replace with your actual frontend reset URL

                    // MODIFIED: Publish password reset requested event
                    return notificationEventPublisherService.publishPasswordResetRequestedEvent(
                            user.getPrimaryIdentifierType(),
                            String.valueOf(user.getId()),
                            identifier,
                            user.getFirstName(),
                            resetLink
                    );
                })
                .doOnError(e -> log.error("Error initiating password reset for {}: {}", identifier, e.getMessage(), e));
    }

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
     * Creates a new user in the backend database and then registers them in the
     * Authorization Server. This method implements the "backend-first" hybrid
     * registration approach. If Authorization Server registration fails, the
     * user is rolled back (deleted) from the backend database. After successful
     * registration, it initiates the email verification process by sending an
     * OTP.
     *
     * @param request The DTO containing user creation data, including password.
     * @return A Mono emitting the created User object, updated with
     * Authorization Server's authId.
     * @throws DuplicateResourceException if a user with the same email or
     * phoneNumber already exists in backend DB or Authorization Server.
     * @throws RoleNotFoundException if a specified role does not exist.
     * @throws RuntimeException if Authorization Server registration fails for
     * other reasons.
     * @throws EmailSendingException if the initial verification email fails to
     * send.
     */
    @Transactional
    public Mono<User> createUser(UserProfileCreateRequest request) {
        log.debug("Attempting to create user with identifier type: {}", request.getIdentifierType());

        Mono<Boolean> emailExistsMono = Mono.just(false);
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            emailExistsMono = userRepository.existsByEmail(request.getEmail());
        }

        Mono<Boolean> phoneNumberExistsMono = Mono.just(false);
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            phoneNumberExistsMono = userRepository.existsByPhoneNumber(request.getPhoneNumber());
        }

        String primaryIdentifier = null;

        if (null == request.getIdentifierType()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_IDENTIFIER_TYPE);
        } else {
            switch (request.getIdentifierType()) {
                case IdentifierType.EMAIL ->
                    primaryIdentifier = request.getEmail();
                case IdentifierType.PHONE_NUMBER ->
                    primaryIdentifier = request.getPhoneNumber();
                default ->
                    throw new IllegalArgumentException(ApiResponseMessages.INVALID_IDENTIFIER_TYPE);
            }
        }

        final String userPrimaryIdentifier = primaryIdentifier;

        return Mono.zip(
                emailExistsMono, phoneNumberExistsMono
        ).flatMap(tuple -> {
            boolean emailExists = tuple.getT1();
            boolean phoneNumberExists = tuple.getT2();

            if (emailExists) {
                return Mono.error(new DuplicateResourceException(ApiResponseMessages.EMAIL_ALREADY_EXISTS));
            }
            if (phoneNumberExists) {
                return Mono.error(new DuplicateResourceException(ApiResponseMessages.PHONE_NUMBER_ALREADY_EXISTS));
            }

            // Create User entity for backend DB
            User newUser = new User();
            newUser.setPrimaryIdentifier(userPrimaryIdentifier);
            newUser.setEmail(request.getEmail());
            newUser.setFirstName(request.getFirstName());
            newUser.setLastName(request.getLastName());
            newUser.setPhoneNumber(request.getPhoneNumber());
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            newUser.setEnabled(true);
            newUser.setEmailVerified(false); // Default
            newUser.setPhoneVerified(false); // Default
            newUser.setPrimaryIdentifierType(request.getIdentifierType());

            // Ensure roles are provided and valid
            if (request.getRoles() == null || request.getRoles().isEmpty()) {
                log.warn("User creation failed in backend: No role assigned for new user.");
                return Mono.error(new InvalidUserDataException(ApiResponseMessages.NO_ROLE_ASSIGNED_FOR_USER));
            }

            Set<Mono<Role>> roleMonos = request.getRoles().stream()
                    .map(roleName -> roleRepository.findByName(roleName)
                    .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND + " : " + roleName))))
                    .collect(Collectors.toSet());

            return Flux.fromIterable(roleMonos)
                    .flatMap(mono -> mono)
                    .collect(Collectors.toSet())
                    .flatMap(newRoles -> {
                        newUser.setRoles(newRoles);
                        return userRepository.save(newUser);
                    })
                    .flatMap(persistedUser -> {
                        Long userId = persistedUser.getId();
                        log.info("User '{}' created successfully in backend DB with internal ID: {}. Proceeding to Authorization Server registration.", userPrimaryIdentifier, userId);
                        // 2. Register user in Authorization Server via Admin API
                        return iAdminService.createUserInAuthServer(persistedUser)
                                .flatMap(authServerAuthId -> {
                                    // 3. Update backend user with Authorization Server's authId
                                    persistedUser.setAuthId(authServerAuthId);
                                    log.info("User registered in Authorization Server with Auth ID: {}. Updating backend user.", authServerAuthId);
                                    return userRepository.save(persistedUser); // Update the user in backend DB with Auth Server Auth ID
                                })
                                .flatMap(updatedUserAfterAuthId -> {
                                    // 4. Generate OTP, store it, and publish email verification event
                                    log.info("Generating OTP and publishing email verification event for user {} (Auth ID: {}).", updatedUserAfterAuthId.getId(), updatedUserAfterAuthId.getAuthId());
                                    return otpService.generateAndStoreOtp(updatedUserAfterAuthId.getAuthId(), EMAIL_OTP_VALIDITY)
                                            .flatMap(otpCode
                                                    -> notificationEventPublisherService.publishEmailVerificationRequestedEvent(
                                                    updatedUserAfterAuthId.getAuthId(),
                                                    updatedUserAfterAuthId.getEmail(),
                                                    updatedUserAfterAuthId.getFirstName(),
                                                    otpCode // Pass the actual OTP code
                                            ).thenReturn(updatedUserAfterAuthId) // Return the user after event is published
                                            );
                                })
                                .onErrorResume(e -> {
                                    // 5. If Authorization Server registration OR Email Verification initiation fails,
                                    //    rollback (delete) user from backend DB and Authorization Server.
                                    log.error("Failed to register user in Authorization Server or send verification email for internal ID {}. Initiating rollback. Error: {}",
                                            userId, e.getMessage(), e);

                                    // Attempt to delete from Auth Server first if an AuthId was assigned, then local DB
                                    Mono<Void> authServerDelete = Mono.empty();
                                    if (persistedUser.getAuthId() != null) {
                                        authServerDelete = iAdminService.deleteUserFromAuthServer(persistedUser.getAuthId())
                                                .onErrorResume(authDeleteError -> {
                                                    log.error("Error during Auth Server rollback for user {}: {}", persistedUser.getAuthId(), authDeleteError.getMessage());
                                                    return Mono.empty(); // Continue with local delete even if Auth Server delete fails
                                                });
                                    }

                                    return authServerDelete
                                            .then(userRepository.delete(persistedUser))
                                            .then(Mono.error(new RuntimeException(ApiResponseMessages.USER_REGISTRATION_FAILED + ": " + e.getMessage(), e)));
                                });
                    })
                    .flatMap(this::prepareDto)
                    .doOnSuccess(u -> log.debug("User created successfully with ID: {}", u.getId()))
                    .doOnError(e -> log.error("Error creating user {}: {}", userPrimaryIdentifier, e.getMessage(), e));
        });
    }

    /**
     * Validates the provided OTP for email verification and, if valid, marks
     * the user's email as verified in Authorization Server. If the purpose is
     * 'USER_REGISTER', it also sends the onboarding email.
     *
     * @param authServerUserId The Authorization Server user ID.
     * @param providedOtp The OTP code provided by the user.
     * @param purpose The purpose of the OTP verification (e.g.,
     * "USER_REGISTER"). // NEW PARAM
     * @return Mono<Boolean> true if verification successful, false otherwise.
     * @throws OtpValidationException if the OTP is invalid or expired.
     * @throws UserNotFoundException if the user is not found in Authorization
     * Server.
     * @throws RuntimeException for other internal errors.
     */
    public Mono<Boolean> verifyEmailOtp(String authServerUserId, String providedOtp, String purpose) {
        log.info("Attempting to verify email OTP for user: {} with purpose: {}", authServerUserId, purpose);
        return otpService.validateOtp(authServerUserId, providedOtp)
                .flatMap(isValid -> {
                    if (isValid) {
                        log.info("OTP valid for user {}. Updating email verified status in authorization server and local DB.", authServerUserId);
                        // Update authorization server first
                        return iAdminService.updateEmailVerifiedStatus(authServerUserId, true)
                                .flatMap(isAuthServerUpdated -> {
                                    if (isAuthServerUpdated) {
                                        // If authorization server update succeeds, proceed with local DB update and event publishing
                                        return userRepository.findByAuthId(authServerUserId)
                                                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found in local DB for Auth ID: " + authServerUserId)))
                                                .flatMap(user -> {
                                                    user.setEmailVerified(true);
                                                    return userRepository.save(user); // Attempt to save to local DB
                                                })
                                                .flatMap(user -> {
                                                    // Local DB update succeeded, now publish event
                                                    if (USER_REGISTER.equals(purpose)) {
                                                        log.info("Email verification purpose is USER_REGISTER for primary identifier user {}. Sending onboarding email.", authServerUserId);
                                                        String loginUrl = getLoginPath(); // Replace with your actual login URL
                                                        return notificationEventPublisherService.publishUserRegisteredEvent(
                                                                user.getPrimaryIdentifierType(),
                                                                String.valueOf(user.getId()),
                                                                user.getPrimaryIdentifier(),
                                                                user.getFirstName(),
                                                                loginUrl
                                                        ).thenReturn(user); // Return user to continue the chain
                                                    }
                                                    return Mono.just(user); // No event to publish, just pass the user
                                                })
                                                .thenReturn(true) // Indicate overall success of this branch
                                                .onErrorResume(e -> {
                                                    // If local DB save or event publishing fails, rollback authorization server emailVerified status
                                                    log.error("Local DB update or event publishing failed for user {}. Attempting authorization server emailVerified rollback. Error: {}", authServerUserId, e.getMessage(), e);
                                                    return iAdminService.updateEmailVerifiedStatus(authServerUserId, false) // Rollback authorization server status to false
                                                            .onErrorResume(rollbackError -> {
                                                                log.error("Failed to rollback authorization server emailVerified status for user {}: {}", authServerUserId, rollbackError.getMessage());
                                                                return Mono.error(new RuntimeException("Inconsistency detected: Local DB update failed and authorization server emailVerified rollback also failed for user " + authServerUserId, e));
                                                            })
                                                            .then(Mono.error(e)); // Re-throw the original error after attempting rollback
                                                });
                                    }
                                    return Mono.just(false); // authorization server update failed
                                });
                    }
                    return Mono.just(false);
                })
                .doOnSuccess(v -> log.debug("Email OTP verification process completed for user {}. Status: {}", authServerUserId, v))
                .doOnError(e -> log.error("Error during email OTP verification for user {}: {}", authServerUserId, e.getMessage(), e));
    }

    /**
     * Validates the provided OTP for phone verification and, if valid, marks
     * the user's phone as verified in Authorization Server. If the purpose is
     * 'USER_REGISTER', it also sends the onboarding phone.
     *
     * @param authServerUserId The Authorization Server user ID.
     * @param providedOtp The OTP code provided by the user.
     * @param purpose The purpose of the OTP verification (e.g.,
     * "USER_REGISTER"). // NEW PARAM
     * @return Mono<Boolean> true if verification successful, false otherwise.
     * @throws OtpValidationException if the OTP is invalid or expired.
     * @throws UserNotFoundException if the user is not found in Authorization
     * Server.
     * @throws RuntimeException for other internal errors.
     */
    public Mono<Boolean> verifyPhoneOtp(String authServerUserId, String providedOtp, String purpose) {
        log.info("Attempting to verify phone OTP for user: {} with purpose: {}", authServerUserId, purpose);
        return otpService.validateOtp(authServerUserId, providedOtp)
                .flatMap(isValid -> {
                    if (isValid) {
                        log.info("OTP valid for user {}. Updating phone verified status in authorization server and local DB.", authServerUserId);
                        // Update authorization server first
                        return iAdminService.updatePhoneVerifiedStatus(authServerUserId, true)
                                .flatMap(isAuthServerUpdated -> {
                                    if (isAuthServerUpdated) {
                                        // If authorization server update succeeds, proceed with local DB update and event publishing
                                        return userRepository.findByAuthId(authServerUserId)
                                                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found in local DB for Auth ID: " + authServerUserId)))
                                                .flatMap(user -> {
                                                    user.setPhoneVerified(true);
                                                    return userRepository.save(user); // Attempt to save to local DB
                                                })
                                                .flatMap(user -> {
                                                    // Local DB update succeeded, now publish event
                                                    if (USER_REGISTER.equals(purpose)) {
                                                        log.info("Phone verification purpose is USER_REGISTER for primary identifier user {}. Sending onboarding phone.", authServerUserId);
                                                        String loginUrl = getLoginPath(); // Replace with your actual login URL
                                                        return notificationEventPublisherService.publishUserRegisteredEvent(
                                                                user.getPrimaryIdentifierType(),
                                                                String.valueOf(user.getId()),
                                                                user.getPrimaryIdentifier(),
                                                                user.getFirstName(),
                                                                loginUrl
                                                        ).thenReturn(user); // Return user to continue the chain
                                                    }
                                                    return Mono.just(user); // No event to publish, just pass the user
                                                })
                                                .thenReturn(true) // Indicate overall success of this branch
                                                .onErrorResume(e -> {
                                                    // If local DB save or event publishing fails, rollback authorization server phoneVerified status
                                                    log.error("Local DB update or event publishing failed for user {}. Attempting authorization server phoneVerified rollback. Error: {}", authServerUserId, e.getMessage(), e);
                                                    return iAdminService.updatePhoneVerifiedStatus(authServerUserId, false) // Rollback authorization server status to false
                                                            .onErrorResume(rollbackError -> {
                                                                log.error("Failed to rollback authorization server phoneVerified status for user {}: {}", authServerUserId, rollbackError.getMessage());
                                                                return Mono.error(new RuntimeException("Inconsistency detected: Local DB update failed and authorization server phoneVerified rollback also failed for user " + authServerUserId, e));
                                                            })
                                                            .then(Mono.error(e)); // Re-throw the original error after attempting rollback
                                                });
                                    }
                                    return Mono.just(false); // authorization server update failed
                                });
                    }
                    return Mono.just(false);
                })
                .doOnSuccess(v -> log.debug("Phone OTP verification process completed for user {}. Status: {}", authServerUserId, v))
                .doOnError(e -> log.error("Error during phone OTP verification for user {}: {}", authServerUserId, e.getMessage(), e));
    }

    /**
     * Resends an email verification code (OTP) for a given user. Fetches the
     * user's email from Authorization Server, generates a new OTP, stores it,
     * and publishes a new email verification event.
     *
     * @param authServerUserId The Authorization Server user ID.
     * @return Mono<Void> indicating completion.
     * @throws UserNotFoundException if the user is not found in Authorization
     * Server.
     * @throws IllegalArgumentException if user's email is missing or already
     * verified.
     */
    public Mono<Void> resendEmailVerificationCode(String authServerUserId) {
        log.info("Resending verification code for user: {}", authServerUserId);
        return iAdminService.getUserFromAuthServerById(authServerUserId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found in Authorization Server: " + authServerUserId)))
                .flatMap(userRepresentation -> {
                    String email = userRepresentation.getEmail();
                    String name = userRepresentation.getFirstName(); // Get name for template
                    if (email == null || email.isBlank()) {
                        return Mono.error(new IllegalArgumentException("User does not have an email address to send a code to."));
                    }
                    if (userRepresentation.isEmailVerified() != null && userRepresentation.isEmailVerified()) {
                        return Mono.error(new IllegalArgumentException("Email is already verified for this user."));
                    }

                    log.info("Generating new OTP and publishing resend event for email '{}' for user '{}'.", email, authServerUserId);
                    return otpService.generateAndStoreOtp(authServerUserId, EMAIL_OTP_VALIDITY)
                            .flatMap(otpCode
                                    -> notificationEventPublisherService.publishEmailVerificationRequestedEvent(
                                    authServerUserId,
                                    email,
                                    name,
                                    otpCode // Pass the newly generated OTP
                            )
                            );
                })
                .doOnError(e -> log.error("Error during resend verification code for user {}: {}", authServerUserId, e.getMessage(), e));
    }

    /**
     * Resends an SMS verification code (OTP) for a given user. Fetches the
     * user's SMS from Authorization Server, generates a new OTP, stores it, and
     * publishes a new SMS verification event.
     *
     * @param authServerUserId The Authorization Server user ID.
     * @return Mono<Void> indicating completion.
     * @throws UserNotFoundException if the user is not found in Authorization
     * Server.
     * @throws IllegalArgumentException if user's SMS is missing or already
     * verified.
     */
    public Mono<Void> resendSmsVerificationCode(String authServerUserId) {
        log.info("Resending verification code for user: {}", authServerUserId);
        return userRepository.findByAuthId(authServerUserId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found in Authorization Server: " + authServerUserId)))
                .flatMap(user -> {
                    String phoneNumber = user.getPhoneNumber();
                    String name = user.getFirstName(); // Get name for template
                    if (phoneNumber == null || phoneNumber.isBlank()) {
                        return Mono.error(new IllegalArgumentException("User does not have an SMS address to send a code to."));
                    }
                    log.info("Generating new OTP and publishing resend event for SMS '{}' for user '{}'.", phoneNumber, authServerUserId);
                    return otpService.generateAndStoreOtp(authServerUserId, SMS_OTP_VALIDITY)
                            .flatMap(otpCode
                                    -> notificationEventPublisherService.publishSmsVerificationRequestedEvent(
                                    authServerUserId,
                                    phoneNumber,
                                    name,
                                    otpCode // Pass the newly generated OTP
                            )
                            );
                })
                .doOnError(e -> log.error("Error during resend verification code for user {}: {}", authServerUserId, e.getMessage(), e));
    }

    /**
     * Resends an phoneCall verification code (OTP) for a given user. Fetches
     * the user's phoneCall from Authorization Server, generates a new OTP,
     * stores it, and publishes a new phoneCall verification event.
     *
     * @param authServerUserId The Authorization Server user ID.
     * @return Mono<Void> indicating completion.
     * @throws UserNotFoundException if the user is not found in Authorization
     * Server.
     * @throws IllegalArgumentException if user's phoneCall is missing or
     * already verified.
     */
    public Mono<Void> resendPhoneCallVerificationCode(String authServerUserId) {
        log.info("Resending verification code for user: {}", authServerUserId);
        return userRepository.findByAuthId(authServerUserId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found in Authorization Server: " + authServerUserId)))
                .flatMap(user -> {
                    String phoneNumber = user.getPhoneNumber();
                    String name = user.getFirstName(); // Get name for template
                    if (phoneNumber == null || phoneNumber.isBlank()) {
                        return Mono.error(new IllegalArgumentException("User does not have an phoneCall address to send a code to."));
                    }
                    log.info("Generating new OTP and publishing resend event for phoneCall '{}' for user '{}'.", phoneNumber, authServerUserId);
                    return otpService.generateAndStoreOtp(authServerUserId, PHONE_CALL_OTP_VALIDITY)
                            .flatMap(otpCode
                                    -> notificationEventPublisherService.publishPhoneCallVerificationRequestedEvent(
                                    authServerUserId,
                                    phoneNumber,
                                    name,
                                    otpCode // Pass the newly generated OTP
                            )
                            );
                })
                .doOnError(e -> log.error("Error during resend verification code for user {}: {}", authServerUserId, e.getMessage(), e));
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
                    if (user != null) {
                        log.debug("Found user by ID: {}", id);
                    } else {
                        log.debug("User with ID {} not found.", id);
                    }
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
                    if (user != null) {
                        log.debug("Found user by authorization ID: {}", authId);
                    } else {
                        log.debug("User with authorization ID {} not found.", authId);
                    }
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
     * @throws DuplicateResourceException if the updated email or phoneNumber
     * already exists for another user.
     */
    @Transactional
    public Mono<User> updateUser(Long id, UserRequest userRequest) {
        log.debug("Updating user with ID: {}", id);
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_ID + id)))
                .flatMap(existingUser -> {
                    // Store original values for potential rollback in case local DB save fails
                    String originalEmail = existingUser.getEmail();
                    String originalPhoneNumber = existingUser.getPhoneNumber();
                    boolean originalEmailVerified = existingUser.isEmailVerified();
                    boolean originalPhoneVerified = existingUser.isPhoneVerified();
                    String originalFirstName = existingUser.getFirstName();
                    String originalLastName = existingUser.getLastName();
                    String authServerUserId = existingUser.getAuthId();

                    Mono<Void> authServerEmailUpdate = Mono.empty();
                    Mono<Void> authServerPhoneUpdate = Mono.empty();
                    Mono<Void> authServerFirstNameUpdate = Mono.empty();
                    Mono<Void> authServerLastNameUpdate = Mono.empty();

                    boolean emailChanged = userRequest.getEmail() != null && !userRequest.getEmail().isBlank()
                            && !userRequest.getEmail().equalsIgnoreCase(originalEmail);
                    boolean phoneNumberChanged = userRequest.getPhoneNumber() != null && !userRequest.getPhoneNumber().isBlank()
                            && !userRequest.getPhoneNumber().equalsIgnoreCase(originalPhoneNumber);
                    boolean firstNameChanged = userRequest.getFirstName() != null && !userRequest.getFirstName().isBlank()
                            && !userRequest.getFirstName().equalsIgnoreCase(originalFirstName);
                    boolean lastNameChanged = userRequest.getLastName() != null && !userRequest.getLastName().isBlank()
                            && !userRequest.getLastName().equalsIgnoreCase(originalLastName);

                    if (emailChanged && !IdentifierType.EMAIL.equals(existingUser.getPrimaryIdentifierType())) { // Use enum directly
                        log.info("Email changed for user {}. Updating in authorization server.", existingUser.getId());
                        existingUser.setEmail(userRequest.getEmail());
                        existingUser.setEmailVerified(false); // Mark as unverified upon change

                        authServerEmailUpdate = iAdminService.updateUserAttribute(authServerUserId, email.name(), userRequest.getEmail())
                                .then(iAdminService.updateUserAttribute(authServerUserId, emailVerified.name(), String.valueOf(false)))
                                .doOnSuccess(v -> log.info("Email and verified status updated in authorization server for user {}.", authServerUserId))
                                .onErrorResume(e -> {
                                    log.error("Failed to update email/verified status in authorization server for user {}. Error: {}", authServerUserId, e.getMessage(), e);
                                    return Mono.error(new RuntimeException("Failed to update email in authentication server."));
                                });
                    }

                    if (phoneNumberChanged && !IdentifierType.PHONE_NUMBER.equals(existingUser.getPrimaryIdentifierType())) { // Use enum directly
                        log.info("Phone number changed for user {}. Updating in authorization server.", existingUser.getId());
                        existingUser.setPhoneNumber(userRequest.getPhoneNumber());
                        existingUser.setPhoneVerified(false); // Mark as unverified upon change

                        authServerPhoneUpdate = iAdminService.updateUserAttribute(authServerUserId, phone.name(), userRequest.getPhoneNumber())
                                .then(iAdminService.updateUserAttribute(authServerUserId, phoneVerified.name(), String.valueOf(false)))
                                .doOnSuccess(v -> log.info("Phone updated in authorization server for user {}.", authServerUserId))
                                .onErrorResume(e -> {
                                    log.error("Failed to update phone in authorization server for user {}. Error: {}", authServerUserId, e.getMessage(), e);
                                    return Mono.error(new RuntimeException("Failed to update phone in authentication server."));
                                });
                    }

                    // Apply other updates to existingUser object and prepare authorization server updates
                    if (firstNameChanged) {
                        existingUser.setFirstName(userRequest.getFirstName());
                        authServerFirstNameUpdate = iAdminService.updateUserAttribute(authServerUserId, firstName.name(), userRequest.getFirstName())
                                .doOnSuccess(v -> log.info("First name updated in authorization server for user {}.", authServerUserId))
                                .onErrorResume(e -> {
                                    log.error("Failed to update first name in authorization server for user {}. Error: {}", authServerUserId, e.getMessage(), e);
                                    return Mono.error(new RuntimeException("Failed to update first name in authentication server."));
                                });
                    }
                    if (lastNameChanged) {
                        existingUser.setLastName(userRequest.getLastName());
                        authServerLastNameUpdate = iAdminService.updateUserAttribute(authServerUserId, lastName.name(), userRequest.getLastName())
                                .doOnSuccess(v -> log.info("Last name updated in authorization server for user {}.", authServerUserId))
                                .onErrorResume(e -> {
                                    log.error("Failed to update last name in authorization server for user {}. Error: {}", authServerUserId, e.getMessage(), e);
                                    return Mono.error(new RuntimeException("Failed to update last name in authentication server."));
                                });
                    }
                    if (userRequest.getShippingAddress() != null && !userRequest.getShippingAddress().isBlank()) {
                        existingUser.setShippingAddress(userRequest.getShippingAddress());
                    }
                    existingUser.setUpdatedAt(LocalDateTime.now());

                    Mono<User> saveAndRoleUpdateMono;
                    // Handle roles if provided in UserRequest, otherwise keep existing roles
                    if (userRequest.getRoles() != null && !userRequest.getRoles().isEmpty()) {
                        Set<Mono<Role>> roleMonos = userRequest.getRoles().stream()
                                .map(roleName -> roleRepository.findByName(roleName)
                                .switchIfEmpty(Mono.error(new RoleNotFoundException(ApiResponseMessages.ROLE_NOT_FOUND + " : " + roleName))))
                                .collect(Collectors.toSet());

                        saveAndRoleUpdateMono = Flux.fromIterable(roleMonos)
                                .flatMap(Mono::flux)
                                .collect(Collectors.toSet())
                                .flatMap(newRoles -> {
                                    existingUser.setRoles(newRoles);
                                    return userRepository.save(existingUser);
                                });
                    } else {
                        saveAndRoleUpdateMono = userRepository.save(existingUser);
                    }

                    // Group all authorization server updates (email, phone, first name, last name) to run concurrently
                    Mono<Void> allAuthServerUpdates = Mono.when(
                            authServerEmailUpdate,
                            authServerPhoneUpdate,
                            authServerFirstNameUpdate,
                            authServerLastNameUpdate
                    );

                    // Then, after all authorization server updates, proceed with the local DB save.
                    // Add an onErrorResume block here to implement compensating transactions if saveAndRoleUpdateMono fails.
                    return allAuthServerUpdates.then(saveAndRoleUpdateMono)
                            .onErrorResume(e -> {
                                log.error("Error during user update (local DB save or role update failed). Attempting authorization server rollback. Error: {}", e.getMessage(), e);
                                Mono<Void> authServerRollback = Mono.empty();

                                // If email was changed, attempt to roll it back in authorization server
                                if (emailChanged) {
                                    authServerRollback = authServerRollback.then(
                                            iAdminService.updateUserAttribute(authServerUserId, email.name(), originalEmail)
                                                    .then(iAdminService.updateUserAttribute(authServerUserId, emailVerified.name(), String.valueOf(originalEmailVerified)))
                                                    .doOnSuccess(v -> log.warn("Authorization server email attributes rolled back for user {}.", authServerUserId))
                                                    .onErrorResume(rollbackE -> {
                                                        log.error("CRITICAL: Failed to rollback email in authorization server for user {}. Manual intervention may be required. Error: {}", authServerUserId, rollbackE.getMessage());
                                                        return Mono.empty(); // Continue, but this is a serious issue
                                                    })
                                    );
                                }

                                // If phone number was changed, attempt to roll it back in authorization server
                                if (phoneNumberChanged) {
                                    authServerRollback = authServerRollback.then(
                                            iAdminService.updateUserAttribute(authServerUserId, phone.name(), originalPhoneNumber)
                                                    .then(iAdminService.updateUserAttribute(authServerUserId, phoneVerified.name(), String.valueOf(originalPhoneVerified)))
                                                    .doOnSuccess(v -> log.warn("Authorization server phone attributes rolled back for user {}.", authServerUserId))
                                                    .onErrorResume(rollbackE -> {
                                                        log.error("CRITICAL: Failed to rollback phone number in authorization server for user {}. Manual intervention may be required. Error: {}", authServerUserId, rollbackE.getMessage());
                                                        return Mono.empty(); // Continue, but this is a serious issue
                                                    })
                                    );
                                }

                                // If first name was changed, attempt to roll it back in authorization server
                                if (firstNameChanged) {
                                    authServerRollback = authServerRollback.then(
                                            iAdminService.updateUserAttribute(authServerUserId, firstName.name(), originalFirstName)
                                                    .doOnSuccess(v -> log.warn("Authorization server first name rolled back for user {}.", authServerUserId))
                                                    .onErrorResume(rollbackE -> {
                                                        log.error("CRITICAL: Failed to rollback first name in authorization server for user {}. Manual intervention may be required. Error: {}", authServerUserId, rollbackE.getMessage());
                                                        return Mono.empty(); // Continue, but this is a serious issue
                                                    })
                                    );
                                }

                                // If last name was changed, attempt to roll it back in authorization server
                                if (lastNameChanged) {
                                    authServerRollback = authServerRollback.then(
                                            iAdminService.updateUserAttribute(authServerUserId, lastName.name(), originalLastName)
                                                    .doOnSuccess(v -> log.warn("Authorization server last name rolled back for user {}.", authServerUserId))
                                                    .onErrorResume(rollbackE -> {
                                                        log.error("CRITICAL: Failed to rollback last name in authorization server for user {}. Manual intervention may be required. Error: {}", authServerUserId, rollbackE.getMessage());
                                                        return Mono.empty(); // Continue, but this is a serious issue
                                                    })
                                    );
                                }

                                return authServerRollback.then(Mono.error(e)); // Re-throw the original error after attempting rollback
                            });
                })
                .flatMap(this::prepareDto)
                .doOnSuccess(u -> log.debug("User updated successfully with ID: {}", u.getId()))
                .doOnError(e -> log.error("Error updating user with ID {}: {}", id, e.getMessage(), e));
    }

    /**
     * The method is used only if the attributes of the user changed
     * is does not exist in the authorization server to avoid data inconsistentcy
     * accross the local db and the authorization server
     * 
     * e.g lastLoginAt (login time) does not exist at the authorizaton server so 
     * the method can be used to update it
     * 
     * @param user
     * @return 
     */
    public Mono<User> updateUserOnDB(User user) {
        return userRepository.save(user)
               .doOnSuccess(_user -> log.debug("User with id {} save successfully", _user.getId()))
              .doOnError(e -> log.error("Error saving user with  id  {}: {}", user.getId(), e.getMessage(), e));
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

        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_ID + id))) // 1. Ensure user exists
                .flatMap(user -> {
                    String authServerUserId = user.getAuthId();

                    // 2. Prioritize deletion from the Authorization Server (e.g., Keycloak)
                    return iAdminService.deleteUserFromAuthServer(authServerUserId)
                            .doOnSuccess(v -> log.info("User deleted from authorization server (Keycloak) for auth ID: {}", authServerUserId))
                            .onErrorResume(e -> {
                                // If Keycloak deletion fails, log and propagate error.
                                // The local DB remains untouched, preventing inconsistency.
                                log.error("Failed to delete user from authorization server for auth ID {}. Error: {}", authServerUserId, e.getMessage(), e);
                                return Mono.error(new RuntimeException("Failed to delete user from authentication server. Please retry.", e));
                            })
                            // 3. If Authorization Server deletion succeeds, then delete from Local Database
                            .then(userRepository.deleteById(id))
                            .doOnSuccess(v -> log.info("User deleted from local database for ID: {}", id))
                            .onErrorResume(e -> {
                                // CRITICAL INCONSISTENCY: Keycloak deletion succeeded, but local DB failed.
                                // Automatic rollback (re-creating user in Keycloak) for deletion is complex and usually not safe.
                                // This state requires strong logging and potentially a manual reconciliation process.
                                log.error("CRITICAL: Failed to delete user from local database for ID {} AFTER successful authorization server deletion. Manual intervention may be required. Error: {}", id, e.getMessage(), e);
                                return Mono.error(new RuntimeException("Failed to delete user from local database after authorization server deletion. Inconsistency detected."));
                            });
                })
                .doOnSuccess(v -> log.debug("User deletion process completed successfully for ID: {}", id))
                .doOnError(e -> log.error("Overall error during user deletion for ID {}: {}", id, e.getMessage(), e));
    }

    /**
     * Deletes a user by their Authorization Server authorization ID from both
     * backend DB and Authorization Server. This method is typically called by
     * an admin or for cleanup purposes.
     *
     * @param authId The auth ID (ID at the authorization server e.g
     * Authorization Server) of the user to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if user ID is invalid.
     * @throws ResourceNotFoundException if the user is not found.
     */
    @Transactional
    public Mono<Void> deleteUserByAuthId(String authId) {
        log.debug("Attempting to delete user with Auth ID: {}", authId);

        // 1. Find the user by authId to confirm existence before proceeding
        return userRepository.findByAuthId(authId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_AUTH_ID + authId)))
                .flatMap(user -> {
                    // 2. Prioritize deletion from the Authorization Server (e.g., Keycloak)
                    return iAdminService.deleteUserFromAuthServer(authId)
                            .doOnSuccess(v -> log.info("User deleted from authorization server (Keycloak) for auth ID: {}", authId))
                            .onErrorResume(e -> {
                                // If Keycloak deletion fails, log and propagate error.
                                // The local DB remains untouched, preventing inconsistency.
                                log.error("Failed to delete user from authorization server for auth ID {}. Error: {}", authId, e.getMessage(), e);
                                // Depending on the exact exception from iAdminService, you might wrap it differently.
                                // If it's a 'user not found in auth server' type error, you might choose to proceed
                                // with local deletion or throw a specific error. For now, general runtime exception.
                                return Mono.error(new RuntimeException("Failed to delete user from authentication server.", e));
                            })
                            // 3. If Authorization Server deletion succeeds, then delete from Local Database
                            .then(userRepository.deleteById(user.getId())) // Use user's primary ID for local deletion
                            .doOnSuccess(v -> log.info("User deleted from local database for ID: {} (Auth ID: {})", user.getId(), authId))
                            .onErrorResume(e -> {
                                // CRITICAL INCONSISTENCY: Keycloak deletion succeeded, but local DB failed.
                                // Automatic rollback (re-creating user in Keycloak) for deletion is complex and usually not safe.
                                // This state requires strong logging and potentially a manual reconciliation process.
                                log.error("CRITICAL: Failed to delete user from local database for ID {} (Auth ID: {}) AFTER successful authorization server deletion. Manual intervention may be required. Error: {}", user.getId(), authId, e.getMessage(), e);
                                return Mono.error(new RuntimeException("Failed to delete user from local database after authorization server deletion. Inconsistency detected."));
                            });
                })
                .doOnSuccess(v -> log.debug("User deletion process completed successfully for Auth ID: {}", authId))
                .doOnError(e -> log.error("Overall error during user deletion for Auth ID {}: {}", authId, e.getMessage(), e));
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
     * Retrieves a user by their phone number, enriching them.
     *
     * @param phoneNumber The phone number of the user.
     * @return A Mono emitting the User if found (enriched), or an error if not.
     * @throws ResourceNotFoundException if the user is not found.
     */
    public Mono<User> getUserByPhoneNumber(String phoneNumber) {
        log.debug("Retrieving user by phoneNumber: {}", phoneNumber);
        return userRepository.findByPhoneNumber(phoneNumber)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ApiResponseMessages.USER_NOT_FOUND_EMAIL + phoneNumber)))
                .flatMap(this::prepareDto)
                .doOnSuccess(user -> log.debug("User retrieved successfully by phone number: {}", user.getPhoneNumber()))
                .doOnError(e -> log.error("Error retrieving user by phone number {}: {}", phoneNumber, e.getMessage(), e));
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
     * Finds users by phoneNumber or email (case-insensitive, contains) with
     * pagination, enriching each.
     *
     * @param searchTerm The search term for phoneNumber or email.
     * @param pageable Pagination information.
     * @return A Flux emitting matching users (enriched).
     */
    public Flux<User> getUsersByPhoneNumberOrEmail(String searchTerm, Pageable pageable) {
        log.debug("Finding users by phoneNumber or email containing '{}' with pagination: {}", searchTerm, pageable);
        return userRepository.findByPhoneNumberOrEmailContainingIgnoreCase(searchTerm, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.debug("Finished finding users by phoneNumber or email containing '{}' for page {} with size {}.", searchTerm, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error finding users by phoneNumber or email {}: {}", searchTerm, e.getMessage(), e));
    }

    /**
     * Counts users by phoneNumber or email (case-insensitive, contains).
     *
     * @param searchTerm The search term for phoneNumber or email.
     * @return A Mono emitting the count of matching users.
     */
    public Mono<Long> countUsersByPhoneNumberOrEmail(String searchTerm) {
        log.debug("Counting users by phoneNumber or email containing '{}'", searchTerm);
        return userRepository.countByPhoneNumberOrEmailContainingIgnoreCase(searchTerm)
                .doOnSuccess(count -> log.debug("Total count for phoneNumber or email containing '{}': {}", searchTerm, count))
                .doOnError(e -> log.error("Error counting users by phoneNumber or email {}: {}", searchTerm, e.getMessage(), e));
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
                .doOnError(e -> log.error("Error counting users by created after {}: {}", date, e.getMessage(), e));
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
     * Checks if a user with the given phoneNumber exists.
     *
     * @param phoneNumber The phoneNumber to check.
     * @return A Mono emitting true if the user exists, false otherwise.
     */
    public Mono<Boolean> existsByPhoneNumber(String phoneNumber) {
        log.debug("Checking if user exists by phoneNumber: {}", phoneNumber);
        return userRepository.existsByPhoneNumber(phoneNumber)
                .doOnSuccess(exists -> log.debug("User with phoneNumber {} exists: {}", phoneNumber, exists))
                .doOnError(e -> log.error("Error checking user existence by phoneNumber {}: {}", phoneNumber, e.getMessage(), e));
    }

}
