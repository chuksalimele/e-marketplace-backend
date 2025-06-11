package com.aliwudi.marketplace.backend.user.dto;

import lombok.AllArgsConstructor; // From Lombok
import lombok.Data;
import lombok.NoArgsConstructor; // From Lombok

import java.util.List;

@Data
@NoArgsConstructor // For default constructor
@AllArgsConstructor // For constructor with all fields
public class JwtResponse {
    private String token;
    private String type = "Bearer"; // Default token type
    private Long id;
    private String username;
    private String email;
    private List<String> roles;

    // Constructor for login success
    public JwtResponse(String accessToken, Long id, String username, String email, List<String> roles) {
        this.token = accessToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
    }
}