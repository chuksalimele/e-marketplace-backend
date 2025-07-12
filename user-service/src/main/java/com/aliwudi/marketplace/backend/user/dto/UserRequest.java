package com.aliwudi.marketplace.backend.user.dto;

import com.aliwudi.marketplace.backend.user.validation.CreateUserValidation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @NotBlank(message = "User authentication id cannot be empty")
    private String authId;
    

    
    @Email(message = "Email should be a valid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;
    
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid phone number format. Must be in E.164 format (e.g., +1234567890).")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters.")    
    private String phoneNumber;
    
    //@Size(min = 6, max = 100, message = "Password must be at least 6 characters long", groups = CreateUserValidation.class)
    //private String password; //the keycloak or other authorization server is responsible for authentication

    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    @Size(max = 255, message = "Shipping address cannot exceed 255 characters")
    private String shippingAddress;

    // Role names to assign to the user during creation or update.
    // Assuming ERole enum names (e.g., "ROLE_USER", "ROLE_ADMIN")
    private Set<String> roles;
}
