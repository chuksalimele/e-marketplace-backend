/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.aliwudi.marketplace.backend.eureka.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration class for Spring Security in the Eureka Server.
 * Disables CSRF and permits all requests to Eureka endpoints,
 * allowing access to the dashboard without authentication.
 * 
 * This class will configure Spring Security to disable CSRF 
 * (which is typically not needed for Eureka Server) and permit 
 * all requests to the Eureka endpoints, allowing you to access 
 * the dashboard without a login. 
 */
@Configuration
public class WebSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for Eureka Server
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll() // Permit all requests to Eureka endpoints
            );
        return http.build();
    }
}