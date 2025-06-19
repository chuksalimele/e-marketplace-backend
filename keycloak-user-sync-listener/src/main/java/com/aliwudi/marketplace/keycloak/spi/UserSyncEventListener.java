package com.aliwudi.marketplace.keycloak.spi;

import static com.aliwudi.marketplace.backend.common.constants.ApiConstants.*;
import com.aliwudi.marketplace.backend.common.dto.UserProfileCreateRequest;
import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak; // For admin client access
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.UserRepresentation; // For admin client User operations

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.keycloak.admin.client.resource.UserResource;

/**
 * Keycloak Event Listener with robust error handling and compensating transactions
 * for synchronizing new user registrations to an external user profile service.
 */
public class UserSyncEventListener implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(UserSyncEventListener.class);
    private final KeycloakSession session;
    private final String userServiceApiUrl;
    private final String keycloakAuthServerUrl;
    private final String keycloakRealm;
    private final String serviceAccountClientId;
    private final String serviceAccountClientSecret;

    // Admin client for operations like deleting Keycloak users for rollback
    private final Map<String, Keycloak> keycloakAdminClients = new ConcurrentHashMap<>();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UserSyncEventListener(KeycloakSession session,
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
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15)) // Increased timeout
                .build();
        this.objectMapper = new ObjectMapper();
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
                // MODIFIED: 1. Use UserProfileCreateRequest DTO directly
                UserProfileCreateRequest userProfileCreateRequest = new UserProfileCreateRequest();
                userProfileCreateRequest.setAuthId(keycloakUserId); 
                userProfileCreateRequest.setUsername(username);
                userProfileCreateRequest.setEmail(email);
                // Set other fields if they exist in UserProfileCreateRequest and are available from the event details
                // userProfileCreateRequest.setFirstName(event.getDetails().get("first_name"));
                // userProfileCreateRequest.setLastName(event.getDetails().get("last_name"));

                String jsonPayload = objectMapper.writeValueAsString(userProfileCreateRequest);

                // 2. Obtain Service Account Access Token for calling user-service
                String serviceAccountAccessToken = getServiceAccountAccessToken(serviceAccountClientId, serviceAccountClientSecret);
                if (serviceAccountAccessToken == null) {
                    LOG.error("Failed to obtain service account access token for user-service call. User synchronization aborted. Initiating Keycloak user deletion.");
                    // Failure: Cannot obtain a token to call user-service.
                    // Action: Delete the Keycloak user because we can't proceed with syncing their profile.                    
                    deleteKeycloakUser(keycloakUserId); // ROLLBACK 1: Delete Keycloak user if we can't even get token
                    return;
                }

                // 3. Call user-service to create profile
                HttpRequest createProfileRequest = HttpRequest.newBuilder()
                        .uri(URI.create(userServiceApiUrl + USER_PROFILES_CREATE))
                        .header(HEADER_CONTENT_TYPE, MEDIA_TYPE_APPLICATION_JSON) 
                        .header(HEADER_AUTHORIZATION, AUTH_SCHEME_BEARER + serviceAccountAccessToken) 
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> createProfileResponse = httpClient.send(createProfileRequest, HttpResponse.BodyHandlers.ofString());

                if (createProfileResponse.statusCode() >= 200 && createProfileResponse.statusCode() < 300) {
                    LOG.infof("Successfully created profile for user %s (Keycloak ID: %s) in user-service. Status: %d",
                            username, keycloakUserId, createProfileResponse.statusCode());

                    // Parse the response to get the auto-generated Long ID from user-service
                    // Assuming user-service returns a User object with 'id' field in JSON
                    Map<String, Object> responseMap = objectMapper.readValue(createProfileResponse.body(), Map.class);
                    String internalAppId = responseMap.get("id").toString(); // Get the Long ID as String

                    // 4. Update Keycloak user attribute with internal Long ID
                    // Obtain a separate admin client instance for this task if needed, or re-use the existing.
                    Keycloak adminClient = getAdminKeycloakClient();
                    if (adminClient == null) {
                        LOG.errorf("Failed to obtain admin Keycloak client. Cannot update 'user_id' for user %s. Needs manual intervention.", keycloakUserId);
                        // Failure: Cannot get admin client to update Keycloak attribute.
                        // Action: Delete both Keycloak user and user-service profile.                                                
                        deleteKeycloakUser(keycloakUserId); // ROLLBACK 3: Delete Keycloak user
                        deleteUserServiceProfile(serviceAccountAccessToken, keycloakUserId); // ROLLBACK 4: Delete US profile
                        return;
                    }

                    UserResource userResource = adminClient.realm(keycloakRealm).users().get(keycloakUserId);
                    UserRepresentation userRep = userResource.toRepresentation();

                    Map<String, List<String>> attributes = userRep.getAttributes();
                    if (attributes == null) {
                        attributes = new HashMap<>();
                    }
                    attributes.put("user_id", Collections.singletonList(internalAppId));
                    userRep.setAttributes(attributes);

                    try {
                        userResource.update(userRep); // Blocking call
                        LOG.infof("Successfully updated Keycloak user %s (ID: %s) with user_id: %s",
                                username, keycloakUserId, internalAppId);
                    } catch (Exception updateEx) {
                        LOG.errorf(updateEx, "Failed to update Keycloak user '%s' with 'user_id'. Initiating rollback.", keycloakUserId);
                        // Failure: Keycloak attribute update failed.
                        // Action: Delete both Keycloak user and user-service profile.                        
                        deleteKeycloakUser(keycloakUserId); // ROLLBACK 5: Delete Keycloak user
                        deleteUserServiceProfile(serviceAccountAccessToken, keycloakUserId); // ROLLBACK 6: Delete US profile
                    }

                } else if (createProfileResponse.statusCode() == 409) { // Conflict - User profile already exists
                    // --- SCENARIO: User profile already exists (idempotency handled) ---
                    // This is a warning, not an error needing rollback, assuming the previous attempt
                    // might have succeeded, but the listener didn't get the confirmation.
                    // Or, if user-service is designed to accept duplicate Keycloak IDs and return existing.
                    
                    LOG.warnf("User profile for Keycloak ID %s already exists in user-service. Status: %d, Response: %s",
                            keycloakUserId, createProfileResponse.statusCode(), createProfileResponse.body());
                    // Decide if you want to update existing or just log and ignore.
                    // For this example, we proceed as if it was already synced.
                } else {
                    // Failure: user-service profile creation failed (e.g., validation, DB error, 5xx)
                    // Action: Delete the Keycloak user.
                    LOG.errorf("Failed to create user profile for Keycloak ID %s in user-service. Status: %d, Response: %s. Initiating rollback.",
                            keycloakUserId, createProfileResponse.statusCode(), createProfileResponse.body());
                    deleteKeycloakUser(keycloakUserId); // ROLLBACK 2: Delete Keycloak user
                }

            } catch (Exception e) {
                // Failure: Any other unexpected error during the flow (e.g., network issue before response, JSON parsing).
                // Action: Delete the Keycloak user. Cannot reliably delete user-service profile here.                
                LOG.errorf(e, "Unexpected error during user synchronization for Keycloak ID %s. Initiating rollback.", keycloakUserId);
                // Catch all other unexpected errors (e.g., network issues before receiving response)
                deleteKeycloakUser(keycloakUserId); // ROLLBACK 7: Delete Keycloak user
                // Cannot delete user-service profile here reliably as we don't know if it was created
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
        // Cleanup resources if necessary
    }

    // --- Helper Methods ---

    /**
     * Gets a Keycloak Admin client instance for performing admin operations.
     * Caches the instance per realm.
     */
    private Keycloak getAdminKeycloakClient() {
        return keycloakAdminClients.computeIfAbsent(keycloakRealm, realm -> {
            LOG.infof("Initializing Keycloak Admin Client for admin operations in realm: {}", realm);
            return Keycloak.getInstance(
                    keycloakAuthServerUrl,
                    "master", // Admin realm for authentication is usually 'master'
                    serviceAccountClientId, // Client ID of the admin service account
                    serviceAccountClientSecret); // Secret of the admin service account
        });
    }

    /**
     * Obtains an access token for a given service account client.
     *
     * @param clientId The client ID of the service account.
     * @param clientSecret The secret of the service account.
     * @return The access token string, or null if failed.
     */
    private String getServiceAccountAccessToken(String clientId, String clientSecret) {
        try {
            // Using a temporary Keycloak client instance just for token retrieval
            Keycloak tempKeycloakClient = Keycloak.getInstance(
                    keycloakAuthServerUrl,
                    keycloakRealm, // Realm where this service account client is
                    clientId,
                    clientSecret);
            String accessToken = tempKeycloakClient.tokenManager().getAccessTokenString();
            tempKeycloakClient.close(); // Close the temporary client
            return accessToken;
        } catch (Exception e) {
            LOG.errorf(e, "Error obtaining access token for service account client '%s' in realm '%s'",
                    clientId, keycloakRealm);
            return null;
        }
    }

    /**
     * Deletes a user in Keycloak (compensating transaction).
     *
     * @param keycloakUserId The UUID of the user in Keycloak.
     */
    private void deleteKeycloakUser(String keycloakUserId) {
        LOG.warnf("Attempting to delete Keycloak user '%s' as part of rollback.", keycloakUserId);
        try {
            Keycloak adminClient = getAdminKeycloakClient();
            if (adminClient != null) {
                adminClient.realm(keycloakRealm).users().delete(keycloakUserId);
                LOG.infof("Successfully deleted Keycloak user '%s' during rollback.", keycloakUserId);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to delete Keycloak user '%s' during rollback. Manual intervention may be required.", keycloakUserId);
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
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(userServiceApiUrl + "/profiles/delete-to-rollback/" + keycloakUserId)) // Specific internal delete endpoint
                    .header("Authorization", "Bearer " + serviceAccountAccessToken)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.infof("Successfully deleted user-service profile for Keycloak ID '%s' during rollback. Status: %d",
                        keycloakUserId, response.statusCode());
            } else {
                LOG.errorf("Failed to delete user-service profile for Keycloak ID '%s' during rollback. Status: %d, Response: %s. Manual intervention may be required.",
                        keycloakUserId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting user-service profile for Keycloak ID '%s' during rollback. Manual intervention may be required.", keycloakUserId);
        }
    }
}