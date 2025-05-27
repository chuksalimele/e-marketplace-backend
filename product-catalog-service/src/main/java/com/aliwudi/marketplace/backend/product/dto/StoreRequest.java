// StoreRequest.java
package com.marketplace.emarketplacebackend.dto; // or payload.request

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StoreRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String location;
    private String description;
    private String contactInfo;
    private String profileImageUrl;
    private Double rating;
    private String categories; // Comma-separated string if applicable

    @NotNull(message = "Seller ID cannot be null")
    private Long sellerId; // When creating a store, you link it to a seller
}