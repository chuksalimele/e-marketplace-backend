// src/main/java/com/aliwudi/marketplace/backend/user/auth/model/LogoutRequest.java
package com.aliwudi.marketplace.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequest {
    @NotBlank(message = "Refresh token is required for logout")
    private String refreshToken;
}