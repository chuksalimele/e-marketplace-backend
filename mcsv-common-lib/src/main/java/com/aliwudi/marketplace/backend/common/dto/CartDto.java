package com.aliwudi.marketplace.backend.common.dto;

// Removed: import com.fasterxml.jackson.annotation.JsonBackReference; // No longer needed as User object is removed
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartDto {
    private Long id;
    private UserDto user; 
    private Set<CartItemDto> items;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
}