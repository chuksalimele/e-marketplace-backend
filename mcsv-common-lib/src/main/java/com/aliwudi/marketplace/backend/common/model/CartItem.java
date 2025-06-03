package com.aliwudi.marketplace.backend.common.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("cart_items")
public class CartItem {
    @Id
    private Long id;
    private Long cartId;
    private Long productId; 
    private Integer quantity;
    
    @Transient
    private Product product;// skip for db but required for response dto
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;        
}