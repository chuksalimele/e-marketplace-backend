package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.user.dto.LoginRequest; // NEW: Import LoginRequest
import com.aliwudi.marketplace.backend.user.dto.JwtResponse; // NEW: Import JwtResponse
import com.aliwudi.marketplace.backend.user.dto.SignupRequest;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;
import com.aliwudi.marketplace.backend.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager; // NEW: Import ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // NEW: Import UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication; // NEW: Import Authentication
import org.springframework.security.core.GrantedAuthority; // NEW: Import GrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder; // NEW: for context
import org.springframework.security.oauth2.jwt.JwtClaimsSet; // NEW: For JWT claims
import org.springframework.security.oauth2.jwt.JwtEncoder; // NEW: For JWT encoding
import org.springframework.security.oauth2.jwt.JwtEncoderParameters; // NEW: For JWT encoding parameters
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant; // NEW: For JWT expiration
import java.time.temporal.ChronoUnit; // NEW: For JWT expiration units
import java.util.List;
import java.util.stream.Collectors; // NEW: For stream operations

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final ReactiveAuthenticationManager authenticationManager; // NEW: Inject ReactiveAuthenticationManager
    private final JwtEncoder jwtEncoder; // NEW: Inject JwtEncoder

    /**
     * Endpoint for new user registration.
     * Delegates the creation logic to UserService.
     *
     * @param signUpRequest The DTO containing user registration data.
     * @return A Mono emitting the created User object (HTTP 201 Created).
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        UserRequest userRequest = new UserRequest(
                signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                signUpRequest.getPassword(),
                null, null, null, // firstName, lastName, shippingAddress (if not in signup)
                signUpRequest.getRole()
        );
        return userService.createUser(userRequest);
    }

    /**
     * NEW: Endpoint for user login and JWT token issuance.
     *
     * @param loginRequest The DTO containing username and password.
     * @return A Mono emitting JwtResponse with the generated JWT.
     */
    @PostMapping("/signin")
    @ResponseStatus(HttpStatus.OK)
    public Mono<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            )
            .flatMap(authentication -> {
                // Set the authenticated user in the security context
                return ReactiveSecurityContextHolder.withAuthentication(authentication)
                    .then(Mono.just(authentication));
            })
            .flatMap(authentication -> {
                // Get user details from the authenticated object
                org.springframework.security.core.userdetails.User springUser =
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal();

                // Generate JWT claims
                Instant now = Instant.now();
                long expiry = 3600L; // Token expires in 1 hour

                List<String> roles = springUser.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

                JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("self") // Or your service name/domain
                    .issuedAt(now)
                    .expiresAt(now.plus(expiry, ChronoUnit.SECONDS))
                    .subject(springUser.getUsername())
                    .claim("roles", roles) // Add roles as a claim
                    .build();

                // Encode the JWT
                return Mono.fromCallable(() -> this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue())
                    .flatMap(jwtToken -> {
                        // Fetch the full User entity to get ID and email (if not available in UserDetails)
                        return userService.findByUsername(springUser.getUsername())
                            .map(userDetails -> (User) userDetails) // Cast back to your custom User model if necessary
                            .map(userEntity -> new JwtResponse(
                                jwtToken,
                                userEntity.getId(),
                                userEntity.getUsername(),
                                userEntity.getEmail(),
                                roles
                            ));
                    });
            });
    }
}