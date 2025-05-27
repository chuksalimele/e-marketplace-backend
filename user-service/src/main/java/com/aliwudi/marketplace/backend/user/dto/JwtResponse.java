// JwtResponse.java
package com.marketplace.emarketplacebackend.dto;

import lombok.Data; // Lombok for getters/setters

import java.util.List; // For user's roles

@Data // From Lombok
public class JwtResponse {
    private String token;
    private String type = "Bearer"; // Standard type for JWT tokens
    private Long id;
    private String username;
    private String email;
    private List<String> roles; // List of roles (e.g., "ROLE_USER", "ROLE_SELLER")

    public JwtResponse(String accessToken, Long id, String username, String email, List<String> roles) {
        this.token = accessToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
    }
}