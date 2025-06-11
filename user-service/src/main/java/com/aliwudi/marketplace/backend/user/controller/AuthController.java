package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.common.model.User; // Ensure this is your custom User model
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.user.dto.LoginRequest;
import com.aliwudi.marketplace.backend.user.dto.JwtResponse;
import com.aliwudi.marketplace.backend.user.dto.SignupRequest;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;
import com.aliwudi.marketplace.backend.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication; // THIS IS CRUCIAL: Ensure this is imported
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder; // THIS IS CRUCIAL: Ensure this is imported
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.util.context.Context; // NEW: Import reactor.util.context.Context if not already

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import reactor.core.scheduler.Schedulers;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        UserRequest userRequest = new UserRequest(
                signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                signUpRequest.getPassword(),
                null, null, null,
                signUpRequest.getRole()
        );
        return userService.createUser(userRequest);
    }

    @PostMapping("/signin")
    @ResponseStatus(HttpStatus.OK)
    public Mono<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            )
            // Fix for 'then' error: Use subscriberContext to set the Authentication in the Reactor Context
            .flatMap(authentication -> {
                // 'authentication.getPrincipal()' IS VALID here.
                // It returns an Object, which you then cast to org.springframework.security.core.userdetails.User.
                // If 'getPrincipal()' is still "symbol not found", it indicates a deeper classpath issue
                // related to spring-security-core being missing or not recognized.
                org.springframework.security.core.userdetails.User springUser =
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal();

                // Generate JWT claims
                Instant now = Instant.now();
                long expiry = 3600L; // Token expires in 1 hour

                List<String> roles = springUser.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

                JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("self")
                    .issuedAt(now)
                    .expiresAt(now.plus(expiry, ChronoUnit.SECONDS))
                    .subject(springUser.getUsername())
                    .claim("roles", roles)
                    .build();

                // Encode the JWT
                return Mono.fromCallable(() -> this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue())
                    .subscribeOn(Schedulers.boundedElastic()) // Offload blocking call to dedicated thread pool
                    .flatMap(jwtToken -> {
                        // Fetch the full User entity to get ID and email
                        return userService.findByUsername(springUser.getUsername())
                            // IMPORTANT: Ensure your com.aliwudi.marketplace.backend.common.model.User
                            // is correctly mapped here from UserDetails if needed for JwtResponse
                            .map(userDetails -> (User) userDetails) // Cast back to your custom User model
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