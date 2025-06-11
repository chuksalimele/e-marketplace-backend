package com.aliwudi.marketplace.backend.product.cofig; // Adjust package as needed

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository; // Important for statelessness

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebFluxSecurity // Enables Spring Security for reactive applications
public class ProductCatalogServiceSecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disable CSRF for stateless APIs
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable) // Disable basic auth, or configure as needed for internal calls
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable) // Disable form login
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults())) // Expect and validate JWTs
            .authorizeExchange(exchange -> exchange
                .pathMatchers(
                    //"/api/products/auth/**"      // Authentication endpoints 
                    //"/api/products/admin/**"      // Authentication endpoints 

                ).authenticated()
                .anyExchange().permitAll() //Allow the rest endpoints
            )
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance()); // Keep stateless
        return http.build();
    }
}