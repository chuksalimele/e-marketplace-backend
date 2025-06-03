package com.aliwudi.marketplace.backend.orderprocessing.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
// Removed: import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // No longer needed as Product object is removed
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("cart_items")
@Data
@NoArgsConstructor
@AllArgsConstructor // Lombok will generate based on new fields, if you keep this
public class CartItem {

    @Id
    private Long id;
    private Long cartId;
    private Long productId; 
    private Integer quantity;

    public CartItem(Long cartId, Long productId, Integer quantity) {
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        
    }
}