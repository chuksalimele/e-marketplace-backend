package com.aliwudi.marketplace.backend.common.model;

import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("order_items")
public class OrderItem {

    @Id
    private Long id;
    private Long orderId;
    private Long productId; // required for db
    @Transient
    private Product product; // skip for db but required for response dto
    private Integer quantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal priceAtTimeOfOrder; // CRUCIAL: This information is specific to *this* order item
                                          // and must remain here, as product prices can change over time.
    
}