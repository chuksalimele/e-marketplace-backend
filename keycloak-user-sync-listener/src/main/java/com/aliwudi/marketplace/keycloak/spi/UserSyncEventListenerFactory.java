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
    private String userSyncServiceAccountClientId;
    private String userSyncServiceAccountClientSecret;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new UserSyncEventListener(session,
                userServiceApiUrl,
                keycloakAuthServerUrl,
                keycloakRealm,
                userSyncServiceAccountClientId,
                userSyncServiceAccountClientSecret);
    }

    @Override
    public void init(Config.Scope config) {
        this.userServiceApiUrl = config.get("userServiceApiUrl", "http://localhost:5001/api/users");
        this.keycloakAuthServerUrl = config.get("keycloakAuthServerUrl", "http://localhost:8080");
        this.keycloakRealm = config.get("keycloakRealm", "chuks-emaketplace-realm");
        this.userSyncServiceAccountClientId = config.get("userSyncServiceAccountClientId", "user-sync-service-account");
        this.userSyncServiceAccountClientSecret = config.get("userSyncServiceAccountClientSecret", "YOUR_GENERATED_LISTENER_CLIENT_SECRET"); // From Keycloak Admin Console for 'user-sync-service-account'

        LOG.infof("UserSyncEventListener configured. User Service API URL: %s, Keycloak URL: %s, Realm: %s, Listener Client ID: %s",
                userServiceApiUrl, keycloakAuthServerUrl, keycloakRealm, userSyncServiceAccountClientId);
        LOG.warn("Listener Client Secret is configured. ENSURE THIS IS SECURELY MANAGED AND NOT DEFAULT!");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void close() {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}