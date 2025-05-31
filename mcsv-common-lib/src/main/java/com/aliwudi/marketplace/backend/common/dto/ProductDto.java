// ProductDto.java
package com.aliwudi.marketplace.backend.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock; // Make sure this is initialized or set upon creation
    private CategoryDto category;
    private StoreDto store;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
}