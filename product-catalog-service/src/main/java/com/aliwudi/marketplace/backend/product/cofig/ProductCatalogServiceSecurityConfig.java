package com.aliwudi.marketplace.backend.product.cofig; // Adjust package as needed

import com.aliwudi.marketplace.backend.common.util.JwtAuthConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository; // Important for statelessness
import org.springframework.security.oauth2.jwt.Jwt;
import static org.springframework.security.config.Customizer.withDefaults;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity // Enables Spring Security for reactive applications
public class ProductCatalogServiceSecurityConfig {

    
    @Value("${jwt.auth.converter.principle-attribute}")
    private String principleAttribute;
    
    @Value("${jwt.auth.converter.resource-id}")
    private String resourceId;
    
    private Converter<Jwt, ? extends Mono<? extends AbstractAuthenticationToken>> jwtAuthConverter;        
        
    @Bean
    public JwtAuthConverter getJwtAuthConverter(){
        return new JwtAuthConverter(principleAttribute, resourceId);
    }
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disable CSRF for stateless APIs
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable) // Disable basic auth, or configure as needed for internal calls
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable) // Disable form login
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))) // Expect and validate JWTs
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