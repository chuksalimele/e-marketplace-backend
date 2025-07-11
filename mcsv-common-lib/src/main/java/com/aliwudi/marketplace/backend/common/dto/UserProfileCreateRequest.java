package com.aliwudi.marketplace.backend.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Data;

@Data
public class UserProfileCreateRequest {
    
    @NotBlank
    private String authId; // The UUID from Keycloak
    

    @Email(message = "Invalid email format.")
    @Size(max = 100, message = "Email cannot exceed 100 characters.")
    private String email; // No @NotBlank here, as it's conditional

    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid phone number format. Must be in E.164 format (e.g., +1234567890).")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters.")
    private String phoneNumber; // No @NotBlank here, as it's conditional

    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters long.")
    private String password;

    @NotBlank(message = "Identifier type is required (EMAIL or PHONE_NUMBER).")
    @Pattern(regexp = "EMAIL|PHONE_NUMBER", message = "Identifier type must be 'EMAIL' or 'PHONE_NUMBER'.")
    private String identifierType; // NEW: To specify primary identifier

    @Size(max = 50, message = "First name cannot exceed 50 characters.")
    private String firstName;

    @Size(max = 50, message = "Last name cannot exceed 50 characters.")
    private String lastName;
    
    // Add other fields you expect from Authorization server (Keycloak) registration (e.g., firstName, lastName)
    private Set<String> roles;

}