package com.aliwudi.marketplace.backend.common.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("order_items")
public class OrderItem {

    @Id
    private Long id;
    private Long orderId;
    private Long productId; // required for db
    @Transient
    private Product product; // skip for db but required for response dto
    private Integer quantity;
    
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;
    
    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;    
    
    private BigDecimal priceAtTimeOfOrder; // CRUCIAL: This information is specific to *this* order item
                                          // and must remain here, as product prices can change over time.
    
}