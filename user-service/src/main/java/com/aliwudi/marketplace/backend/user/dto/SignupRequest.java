// SignupRequest.java
package com.marketplace.emarketplacebackend.payload.request;

import jakarta.validation.constraints.Email;   // For email format validation
import jakarta.validation.constraints.NotBlank; // For non-empty fields
import jakarta.validation.constraints.Size;   // For string length validation
import lombok.Data; // Lombok for getters/setters

import java.util.Set; // For roles

@Data // From Lombok
public class SignupRequest {
    @NotBlank
    @Size(min = 3, max = 20) // Username must be between 3 and 20 characters
    private String username;

    @NotBlank
    @Size(max = 50)
    @Email // Ensures the email is in a valid format
    private String email;

    // Roles are sent as a Set of Strings (e.g., ["seller", "admin"])
    // These will be mapped to our ERole enum and Role entity later.
    private Set<String> role;

    @NotBlank
    @Size(min = 6, max = 40) // Password must be between 6 and 40 characters
    private String password;
}