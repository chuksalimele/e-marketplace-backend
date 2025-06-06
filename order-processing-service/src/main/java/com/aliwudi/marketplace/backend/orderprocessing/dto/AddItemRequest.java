// AddItemRequest.java
package com.aliwudi.marketplace.backend.orderprocessing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for adding a new item to the cart.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddItemRequest {
    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
}
