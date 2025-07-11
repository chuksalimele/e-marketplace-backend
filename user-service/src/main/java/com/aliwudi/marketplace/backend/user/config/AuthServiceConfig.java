package com.aliwudi.marketplace.backend.user.config;

import com.aliwudi.marketplace.backend.user.auth.service.KeycloakAdminServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.aliwudi.marketplace.backend.user.auth.service.IAdminService;

/**
 * Configuration class to conditionally provide the IAdminService implementation
 based on the 'auth.server.type' property.
 */
@Configuration
public class AuthServiceConfig {

    /**
     * Provides the KeycloakAdminServiceImpl if 'auth.server.type' is set to 'keycloak'.
     * This bean is automatically picked up by Spring's component scan due to @Service on KeycloakAdminServiceImpl
     * but this @Bean method explicitly makes it available as AdminService interface.
     *
     * Note: If KeycloakAdminServiceImpl is already @Service, Spring will find it.
     * We just need to ensure it's the one injected for AdminService.
     * @param keycloakAdminServiceImpl The automatically wired instance of Authorization ServerAdminServiceImpl.
     * @return The IAdminService implementation for Authorization Server.
     */
    @Bean
    @ConditionalOnProperty(name = "auth.server.type", havingValue = "keycloak")
    public IAdminService keycloakAdminService(KeycloakAdminServiceImpl keycloakAdminServiceImpl) {
        return keycloakAdminServiceImpl;
    }

    // You could add other @Bean methods for other IAdminService implementations here, e.g.:
    // @Bean
    // @ConditionalOnProperty(name = "auth.server.type", havingValue = "auth0")
    // public IAdminService auth0AdminService(Auth0AdminServiceImpl auth0AdminServiceImpl) {
    //     return auth0AdminServiceImpl;
    // }
}