package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.user.dto.SignupRequest;
import com.aliwudi.marketplace.backend.user.dto.UserRequest; // Import UserRequest for mapping
import com.aliwudi.marketplace.backend.user.service.UserService; // Import UserService

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor; // For constructor injection
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor // Uses Lombok to generate constructor for final fields
public class AuthController {

    // Inject UserService instead of direct repositories and encoder
    private final UserService userService;

    /**
     * Endpoint for new user registration.
     * Delegates the creation logic to UserService.
     *
     * @param signUpRequest The DTO containing user registration data.
     * @return A Mono emitting the created User object (HTTP 201 Created).
     * @throws IllegalArgumentException if basic signup request data is invalid.
     * @throws com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException if username/email already exists.
     * @throws com.aliwudi.marketplace.backend.common.exception.RoleNotFoundException if a specified role doesn't exist.
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 for successful resource creation
    public Mono<User> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        // Basic controller-level validation for critical fields.
        // More specific validation (e.g., username/email uniqueness, password strength)
        // is handled by UserService and DTO annotations.
        if (signUpRequest.getUsername() == null || signUpRequest.getUsername().isBlank() ||
            signUpRequest.getEmail() == null || signUpRequest.getEmail().isBlank() ||
            signUpRequest.getPassword() == null || signUpRequest.getPassword().isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_USER_CREATION_REQUEST);
        }

        // Map SignupRequest to UserRequest, as UserService expects UserRequest
        UserRequest userRequest = new UserRequest(
                signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                signUpRequest.getPassword(),
                null, // firstName not typically in signup, set to null
                null, // lastName not typically in signup, set to null
                null, // shippingAddress not typically in signup, set to null
                signUpRequest.getRole() // Pass roles from signup request
        );

        // Delegate to the UserService for actual user creation
        return userService.createUser(userRequest);
        // Exceptions (DuplicateResourceException, RoleNotFoundException, IllegalArgumentException
        // from validation errors or explicit throws) will be handled by GlobalExceptionHandler.
    }

    // You might add login endpoint here in a real AuthController
    // Example (pseudo-code, as authentication mechanism is outside this scope and handled by API Gateway):
    // @PostMapping("/signin")
    // @ResponseStatus(HttpStatus.OK)
    // public Mono<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
    //     // This would typically involve authenticating credentials and generating a JWT token.
    //     // Since API Gateway handles JWT, this part might be minimal or an internal call.
    //     return Mono.just(new JwtResponse("mock_jwt_token", loginRequest.getUsername(), List.of("ROLE_USER")));
    // }
}
