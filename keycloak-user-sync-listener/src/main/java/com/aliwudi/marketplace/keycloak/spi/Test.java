package com.aliwudi.marketplace.keycloak.spi;

import static com.aliwudi.marketplace.backend.common.constants.ApiConstants.*;
import com.aliwudi.marketplace.backend.common.dto.UserProfileCreateRequest;
import org.jboss.logging.Logger;
// REMOVED: import org.keycloak.admin.client.Keycloak; // No longer using Admin Client SDK

import org.keycloak.models.KeycloakSession;

import com.fasterxml.jackson.databind.JsonNode; // For easier JSON parsing without direct Map casting
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
// REMOVED: import java.net.http.HttpClient;
// REMOVED: import java.net.http.HttpClient.Version;
// REMOVED: import java.net.http.HttpRequest;
// REMOVED: import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration; // Still relevant for timeout settings
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit; // For OkHttp's timeout units
import java.util.stream.Collectors;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

// NEW IMPORTS FOR OKHTTP3
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.FormBody; // For form-urlencoded body

/**
 * Keycloak Event Listener with robust error handling and compensating
 * transactions for synchronizing new user registrations to an external user
 * profile service. This version uses direct HTTP calls to Keycloak Admin REST
 * API to avoid keycloak-admin-client SDK dependency issues.
 */
public class Test{

    private static final Logger LOG = Logger.getLogger(Test.class);
    private final KeycloakSession session;
    private final String userServiceApiUrl;
    private final String keycloakAuthServerUrl; // Base URL for Keycloak
    private final String keycloakRealm;          // Realm where the event occurred
    private final String serviceAccountClientId; // Client ID for obtaining admin token
    private final String serviceAccountClientSecret; // Secret for obtaining admin token

    private final OkHttpClient okHttpClient; // CHANGED: From HttpClient to OkHttpClient
    private final ObjectMapper objectMapper;

    // MediaType constants for OkHttp
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType FORM_URLENCODED = MediaType.get("application/x-www-form-urlencoded; charset=utf-8");

    public Test(KeycloakSession session,
            String userServiceApiUrl,
            String keycloakAuthServerUrl,
            String keycloakRealm,
            String serviceAccountClientId,
            String serviceAccountClientSecret) {
        this.session = session;
        this.userServiceApiUrl = userServiceApiUrl;
        this.keycloakAuthServerUrl = keycloakAuthServerUrl;
        this.keycloakRealm = keycloakRealm;
        this.serviceAccountClientId = serviceAccountClientId;
        this.serviceAccountClientSecret = serviceAccountClientSecret;

        SSLContext sslContext;
        X509TrustManager trustAllCertsManager; // Hold the trust manager for OkHttp

        try {
            // Create a trust manager that does not validate certificate chains
            trustAllCertsManager = new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                } // Empty array for no specific accepted issuers

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            };

            sslContext = SSLContext.getInstance("SSL"); // Specify TLS protocol
            sslContext.init(null, new TrustManager[]{trustAllCertsManager}, new java.security.SecureRandom());
            
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.errorf(e, "Failed to initialize SSL context for OkHttpClient. This will prevent HTTP client from working.");
            throw new RuntimeException("Failed to initialize SSL context", e);
        } catch (Exception e) {
            LOG.errorf(e, "An unexpected error occurred during SSL context initialization.");
            throw new RuntimeException("Unexpected error during SSL context initialization", e);
        }

          HostnameVerifier trustAllHostnames = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // Trust all hostnames for development
                }
            };

        // CHANGED: Initializing OkHttpClient
        this.okHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), trustAllCertsManager) // Apply custom SSLContext and TrustManager
                .hostnameVerifier(trustAllHostnames)
                .connectTimeout(15, TimeUnit.SECONDS) // OkHttp uses TimeUnit
                .callTimeout(30, TimeUnit.SECONDS) // Overall call timeout
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public void onEvent() {
        
            LOG.infof("New user registration event detected for userId: %s, username: %s, realm: %s",
                    "test user id", "test username", "test realm id");

            String keycloakUserId = "test user id";
            String username = "test username";
            String email = "test email";

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
                    deleteKeycloakUser(keycloakUserId, null); // Cannot delete US profile yet, token failed
                    return;
                }

                // 3. Call user-service to create profile
                // CHANGED: OkHttp Request creation
                RequestBody requestBody = RequestBody.create(jsonPayload, JSON);
                Request createProfileRequest = new Request.Builder()
                        .url(userServiceApiUrl + USER_PROFILES_CREATE)
                        .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + serviceAccountAccessToken)
                        .post(requestBody)
                        .build();

                // CHANGED: OkHttp call execution
                Response createProfileResponse = okHttpClient.newCall(createProfileRequest).execute();

                if (createProfileResponse.isSuccessful()) { // OkHttp's isSuccessful() checks for 2xx status codes
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
                        // Rollback: Delete both Keycloak user and user-service profile
                        deleteKeycloakUser(keycloakUserId, serviceAccountAccessToken);
                        deleteUserServiceUserProfile(serviceAccountAccessToken, keycloakUserId);
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
                // Cannot delete user-service profile here reliably as we don't know if it was created
                deleteKeycloakUser(keycloakUserId, null); // Pass null if token might not be available
            }
        
    }

    public void close() {
        // Cleanup resources if necessary. OkHttpClient manages its connection pool internally.
        // For simpler shutdown if explicit close is needed: okHttpClient.dispatcher().executorService().shutdown();
    }

    // --- Helper Methods using direct HTTP calls to Keycloak Admin API ---
    /**
     * Obtains an access token for a given service account client from
     * Keycloak's token endpoint.
     *
     * @param clientId The client ID of the service account.
     * @param clientSecret The secret of the service account.
     * @return The access token string, or null if failed.
     */
    private String getServiceAccountAccessToken(String clientId, String clientSecret) {
        try {
            // Keycloak token endpoint URL
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakAuthServerUrl, keycloakRealm);

            // CHANGED: OkHttp FormBody for token request
            RequestBody formBody = new FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .build();

            Request tokenRequest = new Request.Builder()
                    .url(tokenUrl)
                    .post(formBody)
                    .build();

            // CHANGED: OkHttp call execution
            Response tokenResponse = okHttpClient.newCall(tokenRequest).execute();

            if (tokenResponse.isSuccessful()) { // OkHttp's isSuccessful() checks for 2xx status codes
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
        // Keycloak Admin API endpoint for user
        String userUrl = String.format("%s/admin/realms/%s/users/%s", keycloakAuthServerUrl, keycloakRealm, keycloakUserId);

        // First, get the current user representation to avoid overwriting other attributes
        // CHANGED: OkHttp Request creation
        Request getRequest = new Request.Builder()
                .url(userUrl)
                .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + adminAccessToken)
                .get()
                .build();

        // CHANGED: OkHttp call execution
        Response getResponse = okHttpClient.newCall(getRequest).execute();

        if (!getResponse.isSuccessful()) {
            throw new RuntimeException(String.format("Failed to get Keycloak user %s. Status: %d, Response: %s",
                    keycloakUserId, getResponse.code(), getResponse.body().string()));
        }

        ObjectNode userRepJson = (ObjectNode) objectMapper.readTree(getResponse.body().string());

        // Update attributes: 'user_id' should be a list of strings in Keycloak
        ObjectNode attributesNode = (ObjectNode) userRepJson.get("attributes");
        if (attributesNode == null) {
            attributesNode = objectMapper.createObjectNode();
            userRepJson.set("attributes", attributesNode);
        }
        ArrayNode userIdList = objectMapper.createArrayNode();
        userIdList.add(internalAppId);
        attributesNode.set("user_id", userIdList);

        String updatedUserPayload = objectMapper.writeValueAsString(userRepJson);

        // Send PUT request to update user
        // CHANGED: OkHttp Request creation
        RequestBody putRequestBody = RequestBody.create(updatedUserPayload, JSON);
        Request putRequest = new Request.Builder()
                .url(userUrl)
                .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + adminAccessToken)
                .put(putRequestBody)
                .build();

        // CHANGED: OkHttp call execution
        Response putResponse = okHttpClient.newCall(putRequest).execute();

        if (!putResponse.isSuccessful()) {
            throw new RuntimeException(String.format("Failed to update Keycloak user %s attribute. Status: %d, Response: %s",
                    keycloakUserId, putResponse.code(), putResponse.body().string()));
        }
    }

    /**
     * Deletes a user in Keycloak using direct Admin REST API call (compensating
     * transaction).
     *
     * @param keycloakUserId The UUID of the user in Keycloak.
     * @param adminAccessToken The admin access token obtained from Keycloak
     * (can be null if token acquisition failed).
     */
    private void deleteKeycloakUser(String keycloakUserId, String adminAccessToken) {
        LOG.warnf("Attempting to delete Keycloak user '%s' as part of rollback.", keycloakUserId);
        if (adminAccessToken == null) {
            LOG.error("Cannot delete Keycloak user because admin access token is null. Manual intervention may be required.");
            return;
        }
        try {
            String userUrl = String.format("%s/admin/realms/%s/users/%s", keycloakAuthServerUrl, keycloakRealm, keycloakUserId);

            // CHANGED: OkHttp Request creation
            Request deleteRequest = new Request.Builder()
                    .url(userUrl)
                    .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + adminAccessToken)
                    .delete() // OkHttp has a dedicated delete() method
                    .build();

            // CHANGED: OkHttp call execution
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
     * Deletes a user profile in user-service (compensating transaction). This
     * calls a specific internal DELETE endpoint on user-service.
     *
     * @param serviceAccountAccessToken Token to authenticate with user-service.
     * @param keycloakUserId The Keycloak ID of the profile to delete.
     */
    private void deleteUserServiceUserProfile(String serviceAccountAccessToken, String keycloakUserId) {
        LOG.warnf("Attempting to delete user-service profile for Keycloak ID '%s' as part of rollback.", keycloakUserId);
        try {
            // CHANGED: OkHttp Request creation
            Request deleteRequest = new Request.Builder()
                    .url(userServiceApiUrl + USER_PROFILES_DELETE_ROLLBACK.replace("{authId}", keycloakUserId))
                    .addHeader(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + serviceAccountAccessToken)
                    .delete() // OkHttp has a dedicated delete() method
                    .build();

            // CHANGED: OkHttp call execution
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

    public static void main(String args[]) {
        KeycloakSession session = null;
        String userServiceApiUrl = "http://localhost:5001/api/users";
        String keycloakAuthServerUrl = "https://localhost:8443";
        String keycloakRealm = "chuks-emaketplace-realm";
        String serviceAccountClientId = "user-sync-service-account";
        String serviceAccountClientSecret = "mUcL4rSe1DutbOUmuRK34c3mpEBVdDi5";
        
        //System.setProperty("javax.net.debug", "all");
        
        Test test = new Test(session,
                userServiceApiUrl,
                keycloakAuthServerUrl,
                keycloakRealm,
                serviceAccountClientId,
                serviceAccountClientSecret);
        
        test.onEvent();
        
    }
}
