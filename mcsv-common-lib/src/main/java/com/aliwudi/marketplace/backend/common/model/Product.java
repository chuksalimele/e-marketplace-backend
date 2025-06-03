// Product.java
package com.aliwudi.marketplace.backend.common.model;

import lombok.AllArgsConstructor;
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
@Table("products")
public class Product {
    @Id
    private Long id;
    private String name;
    private Long storeId;// required for db  
    private Long sellerId;
    private Long locationId;     
    private String description;
    private BigDecimal price;
    private Integer stockQuantity; // Make sure this is initialized or set upon creation
    private String category;
    private Location location;
    private String imageUrl;   
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;     
}