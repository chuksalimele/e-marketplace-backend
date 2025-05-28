// LoginRequest.java
package com.aliwudi.marketplace.backend.user.dto;

import jakarta.validation.constraints.NotBlank; // For validation (ensures field is not null or empty)
import lombok.Data; // Lombok for getters/setters

@Data // From Lombok: Automatically generates getters, setters, toString(), equals(), hashCode()
public class LoginRequest {
    @NotBlank // Ensures the username field is not empty or just whitespace
    private String username;

    @NotBlank // Ensures the password field is not empty or just whitespace
    private String password;
}