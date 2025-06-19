// UserServiceSecurityConfig.java
package com.aliwudi.marketplace.backend.user.config;

import static com.aliwudi.marketplace.backend.common.constants.ApiPaths.*;
import com.aliwudi.marketplace.backend.common.util.JwtAuthConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity // For @PreAuthorize etc.
public class UserServiceSecurityConfig {

    private final ReactiveUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.auth.converter.principle-attribute}")
    private String principleAttribute;

    @Value("${jwt.auth.converter.resource-id}")
    private String resourceId;
    private Converter<Jwt, ? extends Mono<? extends AbstractAuthenticationToken>> jwtAuthConverter;

    @Bean
    public JwtAuthConverter getJwtAuthConverter() {
        return new JwtAuthConverter(principleAttribute, resourceId);
    }

    public UserServiceSecurityConfig(ReactiveUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    // Define the ReactiveAuthenticationManager bean (as previously discussed)
    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager() {
        UserDetailsRepositoryReactiveAuthenticationManager authenticationManager
                = new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder); // Set the password encoder
        return authenticationManager;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disable CSRF for stateless APIs
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable) // Disable basic auth, or configure as needed for internal calls
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable) // Disable form login
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))) // Configure as Resource Server to validate JWTs
                .authorizeExchange(exchange -> exchange

                .pathMatchers(USER_CONTROLLER_BASE + USER_PROFILES_CREATE)
                .hasRole(ROLE_USER_PROFILE_SYNC) // IMPORTANT: Check for the role

                .pathMatchers(USER_CONTROLLER_BASE + USER_PROFILES_UPDATE)
                .hasRole(ROLE_USER_PROFILE_SYNC) // IMPORTANT: Check for the role

                .anyExchange().authenticated() // All other requests require authentication
                )
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance()); // Keep stateless

        return http.build();
    }
}
