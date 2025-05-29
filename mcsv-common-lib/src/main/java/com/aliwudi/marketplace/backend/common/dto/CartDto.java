package com.aliwudi.marketplace.backend.common.dto;

// Removed: import com.fasterxml.jackson.annotation.JsonBackReference; // No longer needed as User object is removed
import com.fasterxml.jackson.annotation.JsonManagedReference; // Still needed for CartItemDto
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor // Lombok will generate default no-args constructor
// AllArgsConstructor might need manual adjustment or removal if you only want specific constructors
// @AllArgsConstructor // Lombok will generate an all-args constructor including userId
public class CartDto {
    private Long id;
    private UserDto user; 
    private Set<CartItemDto> items = new HashSet<>();
    private BigDecimal totalAmount;

}