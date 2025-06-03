// StoreRequest.java
package com.aliwudi.marketplace.backend.product.dto; // or payload.request

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StoreRequest {
    
    private Long id;
    @NotNull(message = "Store name cannot be null")
    private String name; // e.g., "Downtown Branch", "Online Warehouse"
    private Long locationId; // with the it we can get its country, state and city
    private String description;
    private String phoneNumber;//office phone number
    private String address;
    private Double rating; // Store-specific rating
    @NotNull(message = "Seller ID cannot be null")
    private Long sellerId;

}