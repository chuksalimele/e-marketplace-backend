// LoginRequest.java
package com.aliwudi.marketplace.backend.user.dto;

import jakarta.validation.constraints.NotBlank; // For validation (ensures field is not null or empty)
import lombok.Data; // Lombok for getters/setters

@Data // Generates getters, setters, equals, hashCode, and toString
public class LoginRequest {

    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    private String password;
}