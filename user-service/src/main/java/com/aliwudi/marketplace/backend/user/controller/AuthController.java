// AuthController.java
package com.marketplace.emarketplacebackend.controller;

import com.marketplace.emarketplacebackend.model.Role;
import com.marketplace.emarketplacebackend.dto.JwtResponse;
import com.marketplace.emarketplacebackend.dto.MessageResponse;
import com.marketplace.emarketplacebackend.model.ERole;
import com.marketplace.emarketplacebackend.model.User;
import com.marketplace.emarketplacebackend.payload.request.LoginRequest;
import com.marketplace.emarketplacebackend.payload.request.SignupRequest;
import com.marketplace.emarketplacebackend.repository.RoleRepository;
import com.marketplace.emarketplacebackend.repository.UserRepository;
import com.marketplace.emarketplacebackend.security.jwt.JwtUtils;
import com.marketplace.emarketplacebackend.service.UserDetailsImpl;
import jakarta.validation.Valid; // For @Valid annotation
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600) // Adjust for Flutter app's port if different!
                                                                // For example, "http://localhost:50000" or similar
@RestController // Marks this as a REST controller
@RequestMapping("/api/auth") // Base path for authentication endpoints
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager; // To authenticate users

    @Autowired
    UserRepository userRepository; // To save/check users in DB

    @Autowired
    RoleRepository roleRepository; // To get roles from DB

    @Autowired
    PasswordEncoder encoder; // To hash passwords

    @Autowired
    JwtUtils jwtUtils; // To generate JWTs

    // --- User Sign-In (Login) ---
    // Handles POST requests to /api/auth/signin
    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        // Authenticate the user using Spring Security's AuthenticationManager
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        // If authentication is successful, set the authenticated user in the SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Generate a JWT token for the authenticated user
        String jwt = jwtUtils.generateJwtToken(authentication);

        // Get the UserDetails (our custom UserDetailsImpl) from the authentication object
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Extract user roles and convert them to a list of strings
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        // Return the JWT token and user info in the response
        return ResponseEntity.ok(new JwtResponse(jwt,
                                                userDetails.getId(),
                                                userDetails.getUsername(),
                                                userDetails.getEmail(),
                                                roles));
    }

    // --- User Sign-Up (Registration) ---
    // Handles POST requests to /api/auth/signup
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        // Check if username already exists
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        // Check if email already exists
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User(signUpRequest.getUsername(),
                             signUpRequest.getEmail(),
                             encoder.encode(signUpRequest.getPassword())); // Hash the password!

        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            // If no role is specified, default to ROLE_USER
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role 'ROLE_USER' is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role 'ROLE_ADMIN' is not found."));
                        roles.add(adminRole);
                        break;
                    case "seller":
                        Role sellerRole = roleRepository.findByName(ERole.ROLE_SELLER)
                                .orElseThrow(() -> new RuntimeException("Error: Role 'ROLE_SELLER' is not found."));
                        roles.add(sellerRole);
                        break;
                    default: // Default to user role if unknown or "user" is specified
                        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role 'ROLE_USER' is not found."));
                        roles.add(userRole);
                }
            });
        }

        user.setRoles(roles); // Set the roles for the new user
        userRepository.save(user); // Save the new user to the database

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}