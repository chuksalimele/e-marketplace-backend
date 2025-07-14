/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.user.auth.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 * @author user
 */
@Data
@Component
public class KeycloakSettings {
    
    @Value("${keycloak.url}")
    private String url;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    // We might need a secret for public clients if using confidential client access later
    @Value("${keycloak.client-secret:}") // @Value with default empty string for optional secret
    private String clientSecret;
    
    @Value("${keycloak.username}") 
    private String username;

    @Value("${keycloak.password}") 
    private String password;    
}
