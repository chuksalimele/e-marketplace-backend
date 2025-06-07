package com.aliwudi.marketplace.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Set;

/**
 * DTO for user registration (sign-up).
 * Contains only necessary fields for initial registration.
 */
@Data // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor // Generates a no-argument constructor
@AllArgsConstructor // Generates a constructor with all fields
public class SignupRequest {

    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email should be a valid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters long")
    private String password;

    // Optional: Roles to assign during signup. If not provided,
    // UserService will default to ROLE_USER.
    private Set<String> role;
}
