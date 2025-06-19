package com.aliwudi.marketplace.backend.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserProfileCreateRequest {
    
    @NotBlank
    private String authId; // The UUID from Keycloak
    
    @NotBlank
    private String username;
    
    @NotBlank
    @Email
    private String email;

    // Add other fields you expect from Authorization server (Keycloak) registration (e.g., firstName, lastName)
    private String phoneNumber;    
    private String firstName;
    private String lastName;
}