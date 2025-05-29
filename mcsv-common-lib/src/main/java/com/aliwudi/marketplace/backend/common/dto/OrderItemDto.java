package com.aliwudi.marketplace.backend.common.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;


@Data // Generates getters, setters, equals, hashCode, toString
@NoArgsConstructor // Generates no-argument constructor
// @AllArgsConstructor // Lombok will generate a constructor with all fields (id, order, productId, quantity, priceAtTimeOfOrder)
public class OrderItemDto {

    private Long id;
    private ProductDto product;
    private Integer quantity;
    private BigDecimal priceAtTimeOfOrder;
    
}