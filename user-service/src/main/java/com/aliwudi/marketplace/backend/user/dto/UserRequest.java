package com.aliwudi.marketplace.backend.user.dto;

import com.aliwudi.marketplace.backend.user.validation.CreateUserValidation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Set; // For roles

/**
 * DTO for creating and updating user information.
 * Uses Jakarta Validation annotations for basic input validation.
 */
@Data // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor // Generates a no-argument constructor
@AllArgsConstructor // Generates a constructor with all fields
public class UserRequest {

    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email should be a valid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    // Password is only for creation, or for specific password update requests.
    // For general user updates, a separate DTO for password might be better,
    // or handle null check in service. Keeping it here for initial creation.
    // It is deliberately allowed to be null/blank for update operations as not all updates change password.
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters long", groups = CreateUserValidation.class)
    private String password;

    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    @Size(max = 255, message = "Shipping address cannot exceed 255 characters")
    private String shippingAddress;

    // Role names to assign to the user during creation or update.
    // Assuming ERole enum names (e.g., "ROLE_USER", "ROLE_ADMIN")
    private Set<String> roleNames;
}
