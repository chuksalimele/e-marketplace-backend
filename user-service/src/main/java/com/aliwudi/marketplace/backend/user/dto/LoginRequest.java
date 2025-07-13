// LoginRequest.java
package com.aliwudi.marketplace.backend.user.dto;

import jakarta.validation.constraints.NotBlank; // For validation (ensures field is not null or empty)
import lombok.Data; // Lombok for getters/setters

@Data // Generates getters, setters, equals, hashCode, and toString
public class LoginRequest {

    //@NotBlank(message = "User identifier type must be specified")
    //private String IdentifierType;
    
    @NotBlank(message = "Email or phone number cannot be empty")
    private String userIdentifier;

    @NotBlank(message = "Password cannot be empty")
    private String password;
}