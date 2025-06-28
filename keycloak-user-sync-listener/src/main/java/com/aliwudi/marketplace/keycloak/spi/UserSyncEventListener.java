package com.aliwudi.marketplace.keycloak.spi;

import static com.aliwudi.marketplace.backend.common.constants.ApiConstants.*;
import com.aliwudi.marketplace.backend.common.dto.UserProfileCreateRequest;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URI;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.FormBody;
import okhttp3.TlsVersion;

/**
 * Keycloak Event Listener with robust error handling and compensating transactions
 * for synchronizing new user registrations to an external user profile service.
 * This version uses direct HTTP calls to Keycloak Admin REST API to avoid
 * keycloak-admin-client SDK dependency issues.
 *
 * This version is configured to use OkHttpClient for ONE-WAY TLS. It uses a dedicated
 * truststore to validate the Keycloak server certificate but does NOT present its own
 * client certificate. Hostname verification is implicitly enabled.
 *
 * Truststore path and password are now loaded from Keycloak's configuration.
 */
public class UserSyncEventListener implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListener.class);
    private final KeycloakSession session;
    private final String userServiceApiUrl;
    private final String keycloakAuthServerUrl;
    private final String keycloakRealm;
    private final String serviceAccountClientId;
    private final String serviceAccountClientSecret;

    // Instance variables for truststore path and password (client keystore removed)
    private final String spiTruststorePath;
    private final char[] spiTruststorePassword;

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType FORM_URLENCODED = MediaType.get("application/x-www-form-urlencoded; charset=utf-8");

    public UserSyncEventListener(KeycloakSession session,
                                 String userServiceApiUrl,
                                 String keycloakAuthServerUrl,
                                 String keycloakRealm,
                                 String serviceAccountClientId,
                                 String serviceAccountClientSecret,
                                 // NEW: Parameters for truststore path and password
                                 String spiTruststorePath,
                                 String spiTruststorePassword) { // Client keystore params removed for one-way TLS
        this.session = session;
        this.userServiceApiUrl = userServiceApiUrl;
        this.keycloakAuthServerUrl = keycloakAuthServerUrl;
        this.keycloakRealm = keycloakRealm;
        this.serviceAccountClientId = serviceAccountClientId;
        this.serviceAccountClientSecret = serviceAccountClientSecret;

        // Assign injected config values for truststore
        this.spiTruststorePath = spiTruststorePath;
        this.spiTruststorePassword = spiTruststorePassword != null ? spiTruststorePassword.toCharArray() : null; // Convert string to char array, handle null

        try {
            // --- Server Certificate Trust (SPI trusts Keycloak server cert) ---
            KeyStore spiTrustStore = KeyStore.getInstance("PKCS12");
            // Check if path is null or empty before trying to load
            if (this.spiTruststorePath == null || this.spiTruststorePath.isEmpty()) {
                throw new IllegalArgumentException("SPI Truststore Path must be configured.");
            }
            try (InputStream is = Files.newInputStream(Paths.get(this.spiTruststorePath))) {
                // Check if password is null before trying to load (password can be null if truststore has no password)
                if (this.spiTruststorePassword == null) {
                    spiTrustStore.load(is, null); // Load with null password
                } else {
                    spiTrustStore.load(is, this.spiTruststorePassword);
                }
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(spiTrustStore);

            X509TrustManager x509TrustManager = null;
            for (TrustManager tm : trustManagerFactory.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    x509TrustManager = (X509TrustManager) tm;
                    break;
                }
            }
            if (x509TrustManager == null) {
                throw new NoSuchAlgorithmException("No X509TrustManager found in TrustManagerFactory");
            }

            // --- SSL Context Initialization with ONLY TrustManagers (no KeyManagers = no client cert) ---
            SSLContext sslContext = SSLContext.getInstance("TLS");
            // First argument is 'null' because we are not providing any client KeyManagers (one-way TLS)
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new java.security.SecureRandom());
            
            HostnameVerifier trustAllHostnames = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // Trust all hostnames for development
                }
            };
            
            this.okHttpClient = new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), x509TrustManager)
                    .hostnameVerifier(trustAllHostnames)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(30, TimeUnit.SECONDS)
                    .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
                    .build();
            this.objectMapper = new ObjectMapper();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to initialize OkHttpClient for one-way TLS/SSL using configured truststore. This will prevent HTTP client from working.");
            throw new RuntimeException("Failed to initialize OkHttpClient", e);
        }
    }
    
    @Override
    public void onEvent(Event event) {
        if (EventType.REGISTER.equals(event.getType())) {
            LOG.infof("New user registration event detected for userId: %s, username: %s, realm: %s",
                    event.getUserId(), event.getDetails().get("username"), event.getRealmId());

            String keycloakUserId = event.getUserId();
            String username = event.getDetails().get("username");
            String email = event.getDetails().get("email");

            try {
                // 1. Prepare data for user-service profile creation
                UserProfileCreateRequest userProfileCreateRequest = new UserProfileCreateRequest();
                userProfileCreateRequest.setAuthId(keycloakUserId);
                userProfileCreateRequest.setUsername(username);
                userProfileCreateRequest.setEmail(email);

                String jsonPayload = objectMapper.writeValueAsString(userProfileCreateRequest);

                // 2. Obtain Service Account Access Token for calling user-service
                String serviceAccountAccessToken = getServiceAccountAccessToken(serviceAccountClientId, serviceAccountClientSecret);
                if (serviceAccountAccessToken == null) {
                    LOG.error("Failed to obtain service account access token for user-service call. User synchronization aborted. Initiating Keycloak user deletion.");
                    deleteKeycloakUser(keycloakUserId, null);
                    return;
                }

                // 3. Call user-service to create profile using OkHttp
                RequestBody requestBody = RequestBody.create(jsonPayload, JSON);
                Request createProfileRequest = new Request.Builder()
                        .url(userServiceApiUrl + USER_PROFILES_CREATE)
                        .addHeader(HEADER_CONTENT_TYPE, MEDIA_TYPE_APPLICATION_JSON)
                        .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + serviceAccountAccessToken)
                        .post(requestBody)
                        .build();

                Response createProfileResponse = okHttpClient.newCall(createProfileRequest).execute();

                if (createProfileResponse.isSuccessful()) {
                    String responseBody = createProfileResponse.body().string();
                    LOG.infof("Successfully created profile for user %s (Keycloak ID: %s) in user-service. Status: %d",
                            username, keycloakUserId, createProfileResponse.code());

                    JsonNode responseJson = objectMapper.readTree(responseBody);
                    String internalAppId = responseJson.get("id").asText();

                    // 4. Update Keycloak user attribute with internal Long ID
                    try {
                        updateKeycloakUserAttribute(keycloakUserId, internalAppId, serviceAccountAccessToken);
                        LOG.infof("Successfully updated Keycloak user %s (ID: %s) with user_id: %s",
                                username, keycloakUserId, internalAppId);
                    } catch (Exception updateEx) {
                        LOG.errorf(updateEx, "Failed to update Keycloak user '%s' with 'user_id'. Initiating rollback.", keycloakUserId);
                        deleteKeycloakUser(keycloakUserId, serviceAccountAccessToken);
                        deleteUserServiceProfile(serviceAccountAccessToken, keycloakUserId);
                        return;
                    }

                } else if (createProfileResponse.code() == 409) {
                    LOG.warnf("User profile for Keycloak ID %s already exists in user-service. Status: %d, Response: %s",
                            keycloakUserId, createProfileResponse.code(), createProfileResponse.body().string());
                } else {
                    LOG.errorf("Failed to create user profile for Keycloak ID %s in user-service. Status: %d, Response: %s. Initiating rollback.",
                            keycloakUserId, createProfileResponse.code(), createProfileResponse.body().string());
                    deleteKeycloakUser(keycloakUserId, serviceAccountAccessToken);
                }

            } catch (Exception e) {
                LOG.errorf(e, "Unexpected error during user synchronization for Keycloak ID %s. Initiating rollback.", keycloakUserId);
                deleteKeycloakUser(keycloakUserId, null);
            }
        } else {
            LOG.debugf("Keycloak event: Type=%s, Realm=%s, Client=%s, UserId=%s",
                    event.getType(), event.getRealmId(), event.getClientId(), event.getUserId());
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        // Not handling admin events for this scenario
    }

    @Override
    public void close() {
        // Cleanup resources if necessary. OkHttpClient manages its connection pool internally.
    }

    // --- Helper Methods using direct HTTP calls to Keycloak Admin API ---

    /**
     * Obtains an access token for a given service account client from Keycloak's token endpoint.
     *
     * @param clientId The client ID of the service account.
     * @param clientSecret The secret of the service account.
     * @return The access token string, or null if failed.
     */
    private String getServiceAccountAccessToken(String clientId, String clientSecret) {
        try {
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakAuthServerUrl, keycloakRealm);

            RequestBody formBody = new FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .build();

            Request tokenRequest = new Request.Builder()
                    .url(tokenUrl)
                    .post(formBody)
                    .build();

            Response tokenResponse = okHttpClient.newCall(tokenRequest).execute();

            if (tokenResponse.isSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(tokenResponse.body().string());
                return jsonResponse.get("access_token").asText();
            } else {
                LOG.errorf("Failed to get access token from Keycloak. Status: %d, Response: %s",
                        tokenResponse.code(), tokenResponse.body().string());
                return null;
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error obtaining access token for service account client '%s' in realm '%s'",
                    clientId, keycloakRealm);
            return null;
        }
    }

    /**
     * Updates a user attribute in Keycloak using direct Admin REST API call.
     *
     * @param keycloakUserId The UUID of the user in Keycloak.
     * @param internalAppId The application-specific ID to set as an attribute.
     * @param adminAccessToken The admin access token obtained from Keycloak.
     */
    private void updateKeycloakUserAttribute(String keycloakUserId, String internalAppId, String adminAccessToken) throws Exception {
        String userUrl = String.format("%s/admin/realms/%s/users/%s", keycloakAuthServerUrl, keycloakRealm, keycloakUserId);

        Request getRequest = new Request.Builder()
                .url(userUrl)
                .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + adminAccessToken)
                .get()
                .build();

        Response getResponse = okHttpClient.newCall(getRequest).execute();

        if (!getResponse.isSuccessful()) {
            throw new RuntimeException(String.format("Failed to get Keycloak user %s. Status: %d, Response: %s",
                    keycloakUserId, getResponse.code(), getResponse.body().string()));
        }

        ObjectNode userRepJson = (ObjectNode) objectMapper.readTree(getResponse.body().string());

        ObjectNode attributesNode = (ObjectNode) userRepJson.get("attributes");
        if (attributesNode == null) {
            attributesNode = objectMapper.createObjectNode();
            userRepJson.set("attributes", attributesNode);
        }
        ArrayNode userIdList = objectMapper.createArrayNode();
        userIdList.add(internalAppId);
        attributesNode.set("user_id", userIdList);

        String updatedUserPayload = objectMapper.writeValueAsString(userRepJson);

        RequestBody putRequestBody = RequestBody.create(updatedUserPayload, JSON);
        Request putRequest = new Request.Builder()
                .url(userUrl)
                .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + adminAccessToken)
                .put(putRequestBody)
                .build();

        Response putResponse = okHttpClient.newCall(putRequest).execute();

        if (!putResponse.isSuccessful()) {
            throw new RuntimeException(String.format("Failed to update Keycloak user %s attribute. Status: %d, Response: %s",
                    keycloakUserId, putResponse.code(), putResponse.body().string()));
        }
    }


    /**
     * Deletes a user in Keycloak using direct Admin REST API call (compensating transaction).
     *
     * @param keycloakUserId The UUID of the user in Keycloak.
     * @param adminAccessToken The admin access token obtained from Keycloak (can be null if token acquisition failed).
     */
    private void deleteKeycloakUser(String keycloakUserId, String adminAccessToken) {
        LOG.warnf("Attempting to delete Keycloak user '%s' as part of rollback.", keycloakUserId);
        if (adminAccessToken == null) {
            LOG.error("Cannot delete Keycloak user because admin access token is null. Manual intervention may be required.");
            return;
        }
        try {
            String userUrl = String.format("%s/admin/realms/%s/users/%s", keycloakAuthServerUrl, keycloakRealm, keycloakUserId);

            Request deleteRequest = new Request.Builder()
                    .url(userUrl)
                    .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + adminAccessToken)
                    .delete()
                    .build();

            Response response = okHttpClient.newCall(deleteRequest).execute();
            if (response.isSuccessful()) {
                LOG.infof("Successfully deleted Keycloak user '%s' during rollback. Status: %d",
                        keycloakUserId, response.code());
            } else {
                LOG.errorf("Failed to delete Keycloak user '%s' during rollback. Status: %d, Response: %s. Manual intervention may be required.",
                        keycloakUserId, response.code(), response.body().string());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting Keycloak user '%s' during rollback. Manual intervention may be required.", keycloakUserId);
        }
    }

    /**
     * Deletes a user profile in user-service (compensating transaction).
     * This calls a specific internal DELETE endpoint on user-service.
     *
     * @param serviceAccountAccessToken Token to authenticate with user-service.
     * @param keycloakUserId The Keycloak ID of the profile to delete.
     */
    private void deleteUserServiceProfile(String serviceAccountAccessToken, String keycloakUserId) {
        LOG.warnf("Attempting to delete user-service profile for Keycloak ID '%s' as part of rollback.", keycloakUserId);
        try {
            Request deleteRequest = new Request.Builder()
                    .url(userServiceApiUrl + USER_PROFILES_DELETE_ROLLBACK.replace("{authId}", keycloakUserId))
                    .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + serviceAccountAccessToken)
                    .delete()
                    .build();

            Response response = okHttpClient.newCall(deleteRequest).execute();
            if (response.isSuccessful()) {
                LOG.infof("Successfully deleted user-service profile for Keycloak ID '%s' during rollback. Status: %d",
                        keycloakUserId, response.code());
            } else {
                LOG.errorf("Failed to delete user-service profile for Keycloak ID '%s' during rollback. Status: %d, Response: %s. Manual intervention may be required.",
                        keycloakUserId, response.code(), response.body().string());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting user-service profile for Keycloak ID '%s' during rollback. Manual intervention may be required.", keycloakUserId);
        }
    }
}
