package com.aliwudi.marketplace.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonBackReference;
// Removed: import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // No longer needed as Product object is removed
import jakarta.persistence.*;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CartItemDto {
    private Long id;
    private ProductDto product; 
    private Integer quantity;
    

    // Lombok's @Data will generate getters and setters for 'id', 'cart', 'productId', and 'quantity'.
    // If you had any specific logic in custom 'getProduct()' or 'setProduct()' methods,
    // that logic will need to be moved to your CartService (or a dedicated service layer)
    // and adapted to use the productId and communicate with the Product Catalog Service.
}