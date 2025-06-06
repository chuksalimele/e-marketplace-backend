// UpdateItemQuantityRequest.java
package com.aliwudi.marketplace.backend.orderprocessing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating the quantity of an existing item in the cart.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateItemQuantityRequest {
    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative") // 0 means remove item
    private Integer quantity;
}
