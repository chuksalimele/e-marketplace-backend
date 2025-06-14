package com.aliwudi.marketplace.backend.user.controller;

import com.aliwudi.marketplace.backend.common.exception.DuplicateResourceException;
import com.aliwudi.marketplace.backend.common.exception.InvalidPasswordException;
import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.RoleNotFoundException;
import com.aliwudi.marketplace.backend.common.model.User;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.user.dto.PasswordUpdateRequest;
import com.aliwudi.marketplace.backend.user.dto.UserRequest;
import com.aliwudi.marketplace.backend.user.service.UserService;
import com.aliwudi.marketplace.backend.user.validation.CreateUserValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean; // Use MockBean for Spring context
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// @WebFluxTest annotation focuses on slicing the context to only WebFlux components
// It will auto-configure WebTestClient and scans for @Controller, @ControllerAdvice, etc.
// Use controllers parameter to specify which controller to test
@WebFluxTest(controllers = UserController.class)
// This will replace the actual UserService bean with a Mockito mock in the Spring context
@MockBean(UserService.class)
public class UserControllerTest {

    private WebTestClient webTestClient;

    @MockBean // Mock this dependency as it's injected into the controller
    private UserService userService;

    // We might need to mock @Validated functionality if custom validation groups were not being picked up,
    // but @WebFluxTest typically integrates validation. If tests fail due to validation,
    // consider manually configuring a Validator bean or adjusting the test context.

    @BeforeEach
    void setUp(ApplicationContext applicationContext) {
        // Initialize WebTestClient with the Spring application context
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    // Helper method to create a dummy user
    private User createDummyUser(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setShippingAddress("123 Test St");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    /*
     * --------------------------------------------------------------------------
     * createUser (/api/users/register) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @DisplayName("createUser - Success: Should create a new user and return 201 CREATED")
    void createUser_Success() {
        UserRequest userRequest = new UserRequest("newuser", "test@example.com", "John", "Doe", "123 Main St", Set.of("USER"));
        User createdUser = createDummyUser(1L, "newuser", "test@example.com");

        when(userService.createUser(any(UserRequest.class))).thenReturn(Mono.just(createdUser));

        webTestClient.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(User.class)
                .value(user -> {
                    assertThat(user.getId()).isEqualTo(1L);
                    assertThat(user.getUsername()).isEqualTo("newuser");
                    assertThat(user.getEmail()).isEqualTo("test@example.com");
                });

        verify(userService, times(1)).createUser(any(UserRequest.class));
    }

    @Test
    @DisplayName("createUser - Failure: Should return 400 BAD_REQUEST for invalid input (blank username)")
    void createUser_InvalidInput_BlankUsername() {
        UserRequest userRequest = new UserRequest(" ", "test@example.com",  "John", "Doe", "123 Main St", Set.of("USER"));

        webTestClient.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USER_CREATION_REQUEST); // Or validation error message

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("createUser - Failure: Should return 400 BAD_REQUEST for invalid input (blank email)")
    void createUser_InvalidInput_BlankEmail() {
        UserRequest userRequest = new UserRequest("newuser", " ",  "John", "Doe", "123 Main St", Set.of("USER"));

        webTestClient.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USER_CREATION_REQUEST);

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("createUser - Failure: Should return 409 CONFLICT for duplicate username")
    void createUser_DuplicateUsername() {
        UserRequest userRequest = new UserRequest("existinguser", "test@example.com",  "John", "Doe", "123 Main St", Set.of("USER"));

        when(userService.createUser(any(UserRequest.class))).thenReturn(Mono.error(new DuplicateResourceException("Username 'existinguser' already exists.")));

        webTestClient.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Username 'existinguser' already exists.");

        verify(userService, times(1)).createUser(any(UserRequest.class));
    }

    @Test
    @DisplayName("createUser - Failure: Should return 400 BAD_REQUEST for invalid role name")
    void createUser_RoleNotFound() {
        UserRequest userRequest = new UserRequest("newuser", "test@example.com",  "John", "Doe", "123 Main St", Set.of("NON_EXISTENT_ROLE"));

        when(userService.createUser(any(UserRequest.class))).thenReturn(Mono.error(new RoleNotFoundException("Role(s) not found: [NON_EXISTENT_ROLE]")));

        webTestClient.post().uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isBadRequest() // Assuming RoleNotFoundException maps to 400
                .expectBody()
                .jsonPath("$.message").isEqualTo("Role(s) not found: [NON_EXISTENT_ROLE]");

        verify(userService, times(1)).createUser(any(UserRequest.class));
    }

    /*
     * --------------------------------------------------------------------------
     * updateUser (/api/users/{id}) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"}, authorities = "1") // Simulate admin user with ID 1
    @DisplayName("updateUser - Success: Should update user by ID (ADMIN) and return 200 OK")
    void updateUser_Admin_Success() {
        Long userId = 1L;
        UserRequest userRequest = new UserRequest(null, "updated@example.com", "UpdatedFirstName", null, null, null);
        User updatedUser = createDummyUser(userId, "existinguser", "updated@example.com");
        updatedUser.setFirstName("UpdatedFirstName");

        when(userService.updateUser(eq(userId), any(UserRequest.class))).thenReturn(Mono.just(updatedUser));

        webTestClient.put().uri("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(User.class)
                .value(user -> {
                    assertThat(user.getId()).isEqualTo(userId);
                    assertThat(user.getEmail()).isEqualTo("updated@example.com");
                    assertThat(user.getFirstName()).isEqualTo("UpdatedFirstName");
                });

        verify(userService, times(1)).updateUser(eq(userId), any(UserRequest.class));
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"}, authorities = "1") // Simulate user with ID 1
    @DisplayName("updateUser - Success: Should allow user to update self by ID (USER) and return 200 OK")
    void updateUser_UserSelf_Success() {
        Long userId = 1L;
        UserRequest userRequest = new UserRequest(null, "updated@example.com",  "UpdatedFirstName", null, null, null);
        User updatedUser = createDummyUser(userId, "user1", "updated@example.com");
        updatedUser.setFirstName("UpdatedFirstName");

        when(userService.updateUser(eq(userId), any(UserRequest.class))).thenReturn(Mono.just(updatedUser));

        webTestClient.put().uri("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(User.class)
                .value(user -> {
                    assertThat(user.getId()).isEqualTo(userId);
                    assertThat(user.getEmail()).isEqualTo("updated@example.com");
                    assertThat(user.getFirstName()).isEqualTo("UpdatedFirstName");
                });

        verify(userService, times(1)).updateUser(eq(userId), any(UserRequest.class));
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"}, authorities = "1") // User ID 1
    @DisplayName("updateUser - Failure: Should return 403 FORBIDDEN if user tries to update another user")
    void updateUser_UserOther_Forbidden() {
        Long userId = 2L; // Trying to update user 2
        UserRequest userRequest = new UserRequest(null, "updated@example.com", "UpdatedFirstName", null, null, null);

        webTestClient.put().uri("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isForbidden(); // Expect 403

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("updateUser - Failure: Should return 400 BAD_REQUEST for invalid user ID (ADMIN)")
    void updateUser_InvalidId() {
        UserRequest userRequest = new UserRequest(null, "updated@example.com",  "UpdatedFirstName", null, null, null);

        webTestClient.put().uri("/api/users/{id}", 0L) // Invalid ID
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USER_ID);

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("updateUser - Failure: Should return 404 NOT_FOUND if user not found (ADMIN)")
    void updateUser_NotFound() {
        Long userId = 99L;
        UserRequest userRequest = new UserRequest(null, "updated@example.com", "UpdatedFirstName", null, null, null);

        when(userService.updateUser(eq(userId), any(UserRequest.class))).thenReturn(Mono.error(new ResourceNotFoundException("User not found with ID: " + userId)));

        webTestClient.put().uri("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User not found with ID: " + userId);

        verify(userService, times(1)).updateUser(eq(userId), any(UserRequest.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("updateUser - Failure: Should return 400 BAD_REQUEST for insufficient update data (ADMIN)")
    void updateUser_InsufficientData() {
        Long userId = 1L;
        UserRequest userRequest = new UserRequest(null, null, null, null, null, null); // All null fields

        webTestClient.put().uri("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USER_UPDATE_REQUEST);

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("updateUser - Failure: Should return 409 CONFLICT for duplicate email on update (ADMIN)")
    void updateUser_DuplicateEmail() {
        Long userId = 1L;
        UserRequest userRequest = new UserRequest(null, "duplicate@example.com", null,  null, null, null);

        when(userService.updateUser(eq(userId), any(UserRequest.class))).thenReturn(Mono.error(new DuplicateResourceException("Email 'duplicate@example.com' already in use.")));

        webTestClient.put().uri("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(userRequest))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Email 'duplicate@example.com' already in use.");

        verify(userService, times(1)).updateUser(eq(userId), any(UserRequest.class));
    }

    /*
     * --------------------------------------------------------------------------
     * updateUserPassword (/api/users/{id}/password) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"}, authorities = "1")
    @DisplayName("updateUserPassword - Success: Should update password (ADMIN) and return 204 NO_CONTENT")
    void updateUserPassword_Admin_Success() {
        Long userId = 1L;
        PasswordUpdateRequest passwordUpdateRequest = new PasswordUpdateRequest("oldPass", "newPass123!");

        when(userService.updateUserPassword(eq(userId), any(PasswordUpdateRequest.class))).thenReturn(Mono.empty());

        webTestClient.put().uri("/api/users/{id}/password", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(passwordUpdateRequest))
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty(); // No content expected

        verify(userService, times(1)).updateUserPassword(eq(userId), any(PasswordUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"}, authorities = "1")
    @DisplayName("updateUserPassword - Success: Should allow user to update their own password and return 204 NO_CONTENT")
    void updateUserPassword_UserSelf_Success() {
        Long userId = 1L;
        PasswordUpdateRequest passwordUpdateRequest = new PasswordUpdateRequest("oldPass", "newPass123!");

        when(userService.updateUserPassword(eq(userId), any(PasswordUpdateRequest.class))).thenReturn(Mono.empty());

        webTestClient.put().uri("/api/users/{id}/password", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(passwordUpdateRequest))
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(userService, times(1)).updateUserPassword(eq(userId), any(PasswordUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"}, authorities = "1")
    @DisplayName("updateUserPassword - Failure: Should return 403 FORBIDDEN if user tries to update another user's password")
    void updateUserPassword_UserOther_Forbidden() {
        Long userId = 2L;
        PasswordUpdateRequest passwordUpdateRequest = new PasswordUpdateRequest("oldPass", "newPass123!");

        webTestClient.put().uri("/api/users/{id}/password", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(passwordUpdateRequest))
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("updateUserPassword - Failure: Should return 400 BAD_REQUEST for invalid ID")
    void updateUserPassword_InvalidId() {
        PasswordUpdateRequest passwordUpdateRequest = new PasswordUpdateRequest("oldPass", "newPass123!");

        webTestClient.put().uri("/api/users/{id}/password", 0L)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(passwordUpdateRequest))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USER_ID);

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("updateUserPassword - Failure: Should return 404 NOT_FOUND if user not found")
    void updateUserPassword_NotFound() {
        Long userId = 99L;
        PasswordUpdateRequest passwordUpdateRequest = new PasswordUpdateRequest("oldPass", "newPass123!");

        when(userService.updateUserPassword(eq(userId), any(PasswordUpdateRequest.class))).thenReturn(Mono.error(new ResourceNotFoundException("User not found")));

        webTestClient.put().uri("/api/users/{id}/password", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(passwordUpdateRequest))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User not found");

        verify(userService, times(1)).updateUserPassword(eq(userId), any(PasswordUpdateRequest.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("updateUserPassword - Failure: Should return 400 BAD_REQUEST for invalid old password")
    void updateUserPassword_InvalidOldPassword() {
        Long userId = 1L;
        PasswordUpdateRequest passwordUpdateRequest = new PasswordUpdateRequest("wrongPass", "newPass123!");

        when(userService.updateUserPassword(eq(userId), any(PasswordUpdateRequest.class))).thenReturn(Mono.error(new InvalidPasswordException("Invalid old password")));

        webTestClient.put().uri("/api/users/{id}/password", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(passwordUpdateRequest))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Invalid old password");

        verify(userService, times(1)).updateUserPassword(eq(userId), any(PasswordUpdateRequest.class));
    }

    /*
     * --------------------------------------------------------------------------
     * deleteUser (/api/users/admin/{id}) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("deleteUser - Success: Should delete user by ID (ADMIN) and return 204 NO_CONTENT")
    void deleteUser_Admin_Success() {
        Long userId = 1L;
        when(userService.deleteUser(userId)).thenReturn(Mono.empty());

        webTestClient.delete().uri("/api/users/admin/{id}", userId)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        verify(userService, times(1)).deleteUser(userId);
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("deleteUser - Failure: Should return 403 FORBIDDEN for non-ADMIN user")
    void deleteUser_NonAdmin_Forbidden() {
        Long userId = 1L;
        webTestClient.delete().uri("/api/users/admin/{id}", userId)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("deleteUser - Failure: Should return 400 BAD_REQUEST for invalid ID")
    void deleteUser_InvalidId() {
        webTestClient.delete().uri("/api/users/admin/{id}", 0L)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USER_ID);

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("deleteUser - Failure: Should return 404 NOT_FOUND if user not found")
    void deleteUser_NotFound() {
        Long userId = 99L;
        when(userService.deleteUser(userId)).thenReturn(Mono.error(new ResourceNotFoundException("User not found with ID: " + userId)));

        webTestClient.delete().uri("/api/users/admin/{id}", userId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User not found with ID: " + userId);

        verify(userService, times(1)).deleteUser(userId);
    }

    /*
     * --------------------------------------------------------------------------
     * getUserById (/api/users/{id}) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"}, authorities = "1")
    @DisplayName("getUserById - Success: Should retrieve user by ID (ADMIN) and return 200 OK")
    void getUserById_Admin_Success() {
        Long userId = 1L;
        User user = createDummyUser(userId, "testuser", "test@example.com");
        when(userService.getUserById(userId)).thenReturn(Mono.just(user));

        webTestClient.get().uri("/api/users/{id}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(User.class)
                .value(u -> assertThat(u.getId()).isEqualTo(userId));

        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"}, authorities = "1")
    @DisplayName("getUserById - Success: Should allow user to retrieve self by ID and return 200 OK")
    void getUserById_UserSelf_Success() {
        Long userId = 1L;
        User user = createDummyUser(userId, "user1", "user1@example.com");
        when(userService.getUserById(userId)).thenReturn(Mono.just(user));

        webTestClient.get().uri("/api/users/{id}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(User.class)
                .value(u -> assertThat(u.getId()).isEqualTo(userId));

        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    @WithMockUser(username = "user1", roles = {"USER"}, authorities = "1")
    @DisplayName("getUserById - Failure: Should return 403 FORBIDDEN if user tries to retrieve another user by ID")
    void getUserById_UserOther_Forbidden() {
        Long userId = 2L;
        webTestClient.get().uri("/api/users/{id}", userId)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUserById - Failure: Should return 400 BAD_REQUEST for invalid ID")
    void getUserById_InvalidId() {
        webTestClient.get().uri("/api/users/{id}", 0L)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USER_ID);

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUserById - Failure: Should return 404 NOT_FOUND if user not found")
    void getUserById_NotFound() {
        Long userId = 99L;
        when(userService.getUserById(userId)).thenReturn(Mono.error(new ResourceNotFoundException("User not found with ID: " + userId)));

        webTestClient.get().uri("/api/users/{id}", userId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User not found with ID: " + userId);

        verify(userService, times(1)).getUserById(userId);
    }

    /*
     * --------------------------------------------------------------------------
     * getUserByUsername (/api/users/byUsername/{username}) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUserByUsername - Success: Should retrieve user by username (ADMIN) and return 200 OK")
    void getUserByUsername_Admin_Success() {
        String username = "testuser";
        User user = createDummyUser(1L, username, "test@example.com");
        when(userService.getUserByUsername(username)).thenReturn(Mono.just(user));

        webTestClient.get().uri("/api/users/byUsername/{username}", username)
                .exchange()
                .expectStatus().isOk()
                .expectBody(User.class)
                .value(u -> assertThat(u.getUsername()).isEqualTo(username));

        verify(userService, times(1)).getUserByUsername(username);
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("getUserByUsername - Failure: Should return 403 FORBIDDEN for non-ADMIN user")
    void getUserByUsername_NonAdmin_Forbidden() {
        String username = "testuser";
        webTestClient.get().uri("/api/users/byUsername/{username}", username)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUserByUsername - Failure: Should return 400 BAD_REQUEST for invalid username")
    void getUserByUsername_InvalidUsername() {
        webTestClient.get().uri("/api/users/byUsername/{username}", " ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USERNAME);

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUserByUsername - Failure: Should return 404 NOT_FOUND if user not found")
    void getUserByUsername_NotFound() {
        String username = "nonexistent";
        when(userService.getUserByUsername(username)).thenReturn(Mono.error(new ResourceNotFoundException("User not found with username: " + username)));

        webTestClient.get().uri("/api/users/byUsername/{username}", username)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User not found with username: " + username);

        verify(userService, times(1)).getUserByUsername(username);
    }

    /*
     * --------------------------------------------------------------------------
     * getUserByEmail (/api/users/byEmail/{email}) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUserByEmail - Success: Should retrieve user by email (ADMIN) and return 200 OK")
    void getUserByEmail_Admin_Success() {
        String email = "test@example.com";
        User user = createDummyUser(1L, "testuser", email);
        when(userService.getUserByEmail(email)).thenReturn(Mono.just(user));

        webTestClient.get().uri("/api/users/byEmail/{email}", email)
                .exchange()
                .expectStatus().isOk()
                .expectBody(User.class)
                .value(u -> assertThat(u.getEmail()).isEqualTo(email));

        verify(userService, times(1)).getUserByEmail(email);
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("getUserByEmail - Failure: Should return 403 FORBIDDEN for non-ADMIN user")
    void getUserByEmail_NonAdmin_Forbidden() {
        String email = "test@example.com";
        webTestClient.get().uri("/api/users/byEmail/{email}", email)
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUserByEmail - Failure: Should return 400 BAD_REQUEST for invalid email")
    void getUserByEmail_InvalidEmail() {
        webTestClient.get().uri("/api/users/byEmail/{email}", " ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_EMAIL);

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUserByEmail - Failure: Should return 404 NOT_FOUND if user not found")
    void getUserByEmail_NotFound() {
        String email = "nonexistent@example.com";
        when(userService.getUserByEmail(email)).thenReturn(Mono.error(new ResourceNotFoundException("User not found with email: " + email)));

        webTestClient.get().uri("/api/users/byEmail/{email}", email)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User not found with email: " + email);

        verify(userService, times(1)).getUserByEmail(email);
    }

    /*
     * --------------------------------------------------------------------------
     * getAllUsers (/api/users/admin/all) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getAllUsers - Success: Should retrieve all users with pagination (ADMIN) and return 200 OK")
    void getAllUsers_Admin_Success() {
        List<User> users = Arrays.asList(createDummyUser(1L, "user1", "user1@a.com"), createDummyUser(2L, "user2", "user2@a.com"));
        when(userService.getAllUsers(any(Pageable.class))).thenReturn(Flux.fromIterable(users));

        webTestClient.get().uri("/api/users/admin/all?page=0&size=10&sortBy=username&sortDir=asc")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(User.class)
                .hasSize(2)
                .contains(users.get(0), users.get(1));

        verify(userService, times(1)).getAllUsers(any(Pageable.class));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).getAllUsers(pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(0);
        assertThat(capturedPageable.getPageSize()).isEqualTo(10);
        assertThat(capturedPageable.getSort().toString()).isEqualTo("username: ASC");
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("getAllUsers - Failure: Should return 403 FORBIDDEN for non-ADMIN user")
    void getAllUsers_NonAdmin_Forbidden() {
        webTestClient.get().uri("/api/users/admin/all")
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getAllUsers - Failure: Should return 400 BAD_REQUEST for invalid pagination parameters")
    void getAllUsers_InvalidPagination() {
        webTestClient.get().uri("/api/users/admin/all?page=-1&size=0")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);

        verifyNoInteractions(userService);
    }

    /*
     * --------------------------------------------------------------------------
     * countAllUsers (/api/users/admin/count) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("countAllUsers - Success: Should return total user count (ADMIN) and return 200 OK")
    void countAllUsers_Admin_Success() {
        when(userService.countAllUsers()).thenReturn(Mono.just(5L));

        webTestClient.get().uri("/api/users/admin/count")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(5L);

        verify(userService, times(1)).countAllUsers();
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("countAllUsers - Failure: Should return 403 FORBIDDEN for non-ADMIN user")
    void countAllUsers_NonAdmin_Forbidden() {
        webTestClient.get().uri("/api/users/admin/count")
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(userService);
    }

    /*
     * --------------------------------------------------------------------------
     * getUsersByFirstName (/api/users/admin/byFirstName) & countUsersByFirstName Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUsersByFirstName - Success: Should retrieve users by first name (ADMIN) and return 200 OK")
    void getUsersByFirstName_Admin_Success() {
        String firstName = "John";
        List<User> users = Arrays.asList(createDummyUser(1L, "john1", "john1@a.com"), createDummyUser(2L, "john2", "john2@a.com"));
        when(userService.getUsersByFirstName(eq(firstName), any(Pageable.class))).thenReturn(Flux.fromIterable(users));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/users/admin/byFirstName")
                        .queryParam("firstName", firstName)
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("sortBy", "id")
                        .queryParam("sortDir", "asc")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(User.class)
                .hasSize(2)
                .contains(users.get(0), users.get(1));

        verify(userService, times(1)).getUsersByFirstName(eq(firstName), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUsersByFirstName - Failure: Should return 400 BAD_REQUEST for invalid first name")
    void getUsersByFirstName_InvalidFirstName() {
        webTestClient.get().uri("/api/users/admin/byFirstName?firstName= ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_FIRST_NAME + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("countUsersByFirstName - Success: Should return count of users by first name (ADMIN) and return 200 OK")
    void countUsersByFirstName_Admin_Success() {
        String firstName = "John";
        when(userService.countUsersByFirstName(firstName)).thenReturn(Mono.just(2L));

        webTestClient.get().uri("/api/users/admin/countByFirstName?firstName={firstName}", firstName)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(2L);

        verify(userService, times(1)).countUsersByFirstName(firstName);
    }

    /*
     * --------------------------------------------------------------------------
     * getUsersByLastName (/api/users/admin/byLastName) & countUsersByLastName Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUsersByLastName - Success: Should retrieve users by last name (ADMIN) and return 200 OK")
    void getUsersByLastName_Admin_Success() {
        String lastName = "Doe";
        List<User> users = Arrays.asList(createDummyUser(1L, "user1", "user1@a.com"), createDummyUser(2L, "user2", "user2@a.com"));
        when(userService.getUsersByLastName(eq(lastName), any(Pageable.class))).thenReturn(Flux.fromIterable(users));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/users/admin/byLastName")
                        .queryParam("lastName", lastName)
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("sortBy", "id")
                        .queryParam("sortDir", "asc")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(User.class)
                .hasSize(2)
                .contains(users.get(0), users.get(1));

        verify(userService, times(1)).getUsersByLastName(eq(lastName), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("countUsersByLastName - Success: Should return count of users by last name (ADMIN) and return 200 OK")
    void countUsersByLastName_Admin_Success() {
        String lastName = "Doe";
        when(userService.countUsersByLastName(lastName)).thenReturn(Mono.just(2L));

        webTestClient.get().uri("/api/users/admin/countByLastName?lastName={lastName}", lastName)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(2L);

        verify(userService, times(1)).countUsersByLastName(lastName);
    }

    /*
     * --------------------------------------------------------------------------
     * getUsersByUsernameOrEmail (/api/users/admin/byUsernameOrEmail) & countUsersByUsernameOrEmail Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUsersByUsernameOrEmail - Success: Should retrieve users by username or email (ADMIN) and return 200 OK")
    void getUsersByUsernameOrEmail_Admin_Success() {
        String searchTerm = "user";
        List<User> users = Arrays.asList(createDummyUser(1L, "user1", "user1@a.com"), createDummyUser(2L, "anotheruser", "another@user.com"));
        when(userService.getUsersByUsernameOrEmail(eq(searchTerm), any(Pageable.class))).thenReturn(Flux.fromIterable(users));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/users/admin/byUsernameOrEmail")
                        .queryParam("searchTerm", searchTerm)
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("sortBy", "id")
                        .queryParam("sortDir", "asc")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(User.class)
                .hasSize(2)
                .contains(users.get(0), users.get(1));

        verify(userService, times(1)).getUsersByUsernameOrEmail(eq(searchTerm), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("countUsersByUsernameOrEmail - Success: Should return count of users by username or email (ADMIN) and return 200 OK")
    void countUsersByUsernameOrEmail_Admin_Success() {
        String searchTerm = "user";
        when(userService.countUsersByUsernameOrEmail(searchTerm)).thenReturn(Mono.just(2L));

        webTestClient.get().uri("/api/users/admin/countByUsernameOrEmail?searchTerm={searchTerm}", searchTerm)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(2L);

        verify(userService, times(1)).countUsersByUsernameOrEmail(searchTerm);
    }

    /*
     * --------------------------------------------------------------------------
     * getUsersByCreatedAtAfter (/api/users/admin/byCreatedAtAfter) & countUsersByCreatedAtAfter Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUsersByCreatedAtAfter - Success: Should retrieve users created after date (ADMIN) and return 200 OK")
    void getUsersByCreatedAtAfter_Admin_Success() {
        String dateString = "2023-01-01T00:00:00";
        LocalDateTime dateTime = LocalDateTime.parse(dateString);
        List<User> users = Collections.singletonList(createDummyUser(1L, "lateuser", "late@a.com"));
        when(userService.getUsersByCreatedAtAfter(eq(dateTime), any(Pageable.class))).thenReturn(Flux.fromIterable(users));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/users/admin/byCreatedAtAfter")
                        .queryParam("date", dateString)
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("sortBy", "id")
                        .queryParam("sortDir", "asc")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(User.class)
                .hasSize(1)
                .contains(users.get(0));

        verify(userService, times(1)).getUsersByCreatedAtAfter(eq(dateTime), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUsersByCreatedAtAfter - Failure: Should return 400 BAD_REQUEST for invalid date format")
    void getUsersByCreatedAtAfter_InvalidDateFormat() {
        webTestClient.get().uri("/api/users/admin/byCreatedAtAfter?date=invalid-date")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Invalid date format. Please use ISO 8601 format:YYYY-MM-ddTHH:mm:ss.");

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("countUsersByCreatedAtAfter - Success: Should return count of users created after date (ADMIN) and return 200 OK")
    void countUsersByCreatedAtAfter_Admin_Success() {
        String dateString = "2023-01-01T00:00:00";
        LocalDateTime dateTime = LocalDateTime.parse(dateString);
        when(userService.countUsersByCreatedAtAfter(dateTime)).thenReturn(Mono.just(1L));

        webTestClient.get().uri("/api/users/admin/countByCreatedAtAfter?date={date}", dateString)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(1L);

        verify(userService, times(1)).countUsersByCreatedAtAfter(dateTime);
    }

    /*
     * --------------------------------------------------------------------------
     * getUsersByShippingAddress (/api/users/admin/byShippingAddress) & countUsersByShippingAddress Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("getUsersByShippingAddress - Success: Should retrieve users by shipping address (ADMIN) and return 200 OK")
    void getUsersByShippingAddress_Admin_Success() {
        String address = "Main St";
        List<User> users = Arrays.asList(createDummyUser(1L, "user1", "user1@a.com"), createDummyUser(2L, "user2", "user2@a.com"));
        when(userService.getUsersByShippingAddress(eq(address), any(Pageable.class))).thenReturn(Flux.fromIterable(users));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/users/admin/byShippingAddress")
                        .queryParam("shippingAddress", address)
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .queryParam("sortBy", "id")
                        .queryParam("sortDir", "asc")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(User.class)
                .hasSize(2)
                .contains(users.get(0), users.get(1));

        verify(userService, times(1)).getUsersByShippingAddress(eq(address), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("countUsersByShippingAddress - Success: Should return count of users by shipping address (ADMIN) and return 200 OK")
    void countUsersByShippingAddress_Admin_Success() {
        String address = "Main St";
        when(userService.countUsersByShippingAddress(address)).thenReturn(Mono.just(2L));

        webTestClient.get().uri("/api/users/admin/countByShippingAddress?shippingAddress={address}", address)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(2L);

        verify(userService, times(1)).countUsersByShippingAddress(address);
    }

    /*
     * --------------------------------------------------------------------------
     * existsByEmail (/api/users/existsByEmail) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @DisplayName("existsByEmail - Success: Should return true if email exists and return 200 OK")
    void existsByEmail_True() {
        String email = "existing@example.com";
        when(userService.existsByEmail(email)).thenReturn(Mono.just(true));

        webTestClient.get().uri("/api/users/existsByEmail?email={email}", email)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);

        verify(userService, times(1)).existsByEmail(email);
    }

    @Test
    @DisplayName("existsByEmail - Success: Should return false if email does not exist and return 200 OK")
    void existsByEmail_False() {
        String email = "nonexistent@example.com";
        when(userService.existsByEmail(email)).thenReturn(Mono.just(false));

        webTestClient.get().uri("/api/users/existsByEmail?email={email}", email)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(false);

        verify(userService, times(1)).existsByEmail(email);
    }

    @Test
    @DisplayName("existsByEmail - Failure: Should return 400 BAD_REQUEST for invalid email")
    void existsByEmail_InvalidEmail() {
        webTestClient.get().uri("/api/users/existsByEmail?email= ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_EMAIL);

        verifyNoInteractions(userService);
    }

    /*
     * --------------------------------------------------------------------------
     * existsByUsername (/api/users/existsByUsername) Tests
     * --------------------------------------------------------------------------
     */

    @Test
    @DisplayName("existsByUsername - Success: Should return true if username exists and return 200 OK")
    void existsByUsername_True() {
        String username = "existinguser";
        when(userService.existsByUsername(username)).thenReturn(Mono.just(true));

        webTestClient.get().uri("/api/users/existsByUsername?username={username}", username)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);

        verify(userService, times(1)).existsByUsername(username);
    }

    @Test
    @DisplayName("existsByUsername - Success: Should return false if username does not exist and return 200 OK")
    void existsByUsername_False() {
        String username = "nonexistentuser";
        when(userService.existsByUsername(username)).thenReturn(Mono.just(false));

        webTestClient.get().uri("/api/users/existsByUsername?username={username}", username)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(false);

        verify(userService, times(1)).existsByUsername(username);
    }

    @Test
    @DisplayName("existsByUsername - Failure: Should return 400 BAD_REQUEST for invalid username")
    void existsByUsername_InvalidUsername() {
        webTestClient.get().uri("/api/users/existsByUsername?username= ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo(ApiResponseMessages.INVALID_USERNAME);

        verifyNoInteractions(userService);
    }
}