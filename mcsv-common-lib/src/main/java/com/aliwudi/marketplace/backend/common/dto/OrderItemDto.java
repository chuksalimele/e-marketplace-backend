package com.aliwudi.marketplace.backend.common.dto;

import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemDto {

    private Long id;
    private ProductDto product;
    private Integer quantity;
    private BigDecimal priceAtTimeOfOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
}