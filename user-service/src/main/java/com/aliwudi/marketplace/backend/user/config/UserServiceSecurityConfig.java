// UserServiceSecurityConfig.java
package com.aliwudi.marketplace.backend.user.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtEncoder; // For signing JWTs
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import javax.crypto.spec.SecretKeySpec; // For symmetric key

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebFluxSecurity
public class UserServiceSecurityConfig {

    private final ReactiveUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    // Inject the JWT secret from application.properties
    @Value("${jwt.secret}")
    private String jwtSecret; // Make sure this property is defined in application.properties

    public UserServiceSecurityConfig(ReactiveUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    // Define the ReactiveAuthenticationManager bean (as previously discussed)
    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager() {
        UserDetailsRepositoryReactiveAuthenticationManager authenticationManager =
            new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder); // Set the password encoder
        return authenticationManager;
    }

    // NEW: Define the JwtEncoder bean for creating signed JWTs
    @Bean
    public JwtEncoder jwtEncoder() {
        // Use a symmetric key for HS256 algorithm
        // Ensure your jwtSecret is at least 32 bytes (256 bits) for HS256
        SecretKeySpec secretKey = new SecretKeySpec(jwtSecret.getBytes(), "HmacSha256");
        JWKSource<SecurityContext> immutableSecret = new ImmutableSecret<>(secretKey);
        return new NimbusReactiveJwtEncoder(immutableSecret);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disable CSRF for stateless APIs
                .httpBasic(withDefaults()) // Enable with defaults for basic auth, or configure as needed for internal calls
                .formLogin(withDefaults()) // Enable with defaults for form login
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults())) // Configure as Resource Server to validate JWTs
            .authorizeExchange(exchange -> exchange
                .pathMatchers("/api/auth/**").permitAll() // Allow /api/auth/** for signup/login
                .anyExchange().authenticated() // All other requests require authentication
            )
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance()); // Keep stateless

        return http.build();
    }
}