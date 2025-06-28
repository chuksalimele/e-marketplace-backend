package com.aliwudi.marketplace.keycloak.spi;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class UserSyncEventListenerFactory implements EventListenerProviderFactory {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListenerFactory.class);

    public static final String PROVIDER_ID = "user-sync-listener";

    private String userServiceApiUrl;
    private String keycloakAuthServerUrl;
    private String keycloakRealm;
    private String serviceAccountClientId;
    private String serviceAccountClientSecret;
    // NEW: Configuration for SPI Truststore
    private String spiTruststorePath;
    private String spiTruststorePassword;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        // Pass the configured values to the EventListenerProvider
        return new UserSyncEventListener(session,
                userServiceApiUrl,
                keycloakAuthServerUrl,
                keycloakRealm,
                serviceAccountClientId,
                serviceAccountClientSecret,
                // NEW: Pass truststore config
                spiTruststorePath,
                spiTruststorePassword);
    }

    @Override
    public void init(Config.Scope config) {
        LOG.info("Initializing UserSyncEventListenerFactory...");

        // Read configuration from Keycloak's config scope
        this.userServiceApiUrl = config.get("user-service-api-url");
        this.keycloakAuthServerUrl = config.get("keycloak-auth-server-url");
        this.keycloakRealm = config.get("keycloak-realm");
        this.serviceAccountClientId = config.get("user-sync-service-account-client-id");
        this.serviceAccountClientSecret = config.get("user-sync-service-account-client-secret");

        // NEW: Read SPI Truststore configuration
        this.spiTruststorePath = config.get("spi-truststore-path");
        this.spiTruststorePassword = config.get("spi-truststore-password");

        LOG.infof("UserSyncEventListener configured. User Service API URL: %s, Keycloak URL: %s, Realm: %s, Listener Client ID: %s",
                userServiceApiUrl, keycloakAuthServerUrl, keycloakRealm, serviceAccountClientId);

        if (serviceAccountClientSecret != null && !serviceAccountClientSecret.isEmpty()) {
            LOG.warn("Listener Client Secret is configured. ENSURE THIS IS SECURELY MANAGED AND NOT DEFAULT!");
        }
        // NEW: Log truststore paths for verification
        LOG.infof("SPI Truststore Path: %s, SPI Truststore Password set: %s",
                  spiTruststorePath, (spiTruststorePassword != null && !spiTruststorePassword.isEmpty()));
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Not used for this SPI
    }

    @Override
    public void close() {
        // Not used for this SPI
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}