// src/main/java/com/marketplace/emarketplacebackend/dto/UserProfileUpdateRequest.java
package com.aliwudi.marketplace.backend.user.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {
    // Username is typically immutable or changed via specific process
    // Password is changed via a separate endpoint
    

    @Size(min = 3, max = 50, message = "First name must be between 3 and 50 characters")
    private String firstName;

    @Size(min = 3, max = 50, message = "Last name must be between 3 and 50 characters")
    private String lastName;

    private String phoneNumber;
    private String shippingAddress;
}