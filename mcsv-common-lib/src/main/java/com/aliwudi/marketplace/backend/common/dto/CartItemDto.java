package com.aliwudi.marketplace.backend.common.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto {
    private Long id;
    private ProductDto product; 
    private Integer quantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;        
}