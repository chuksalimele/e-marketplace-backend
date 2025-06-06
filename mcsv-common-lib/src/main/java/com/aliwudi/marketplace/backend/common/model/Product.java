// Product.java
package com.aliwudi.marketplace.backend.common.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;


@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
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
    
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;
    
    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;    
    
    @ToString.Exclude
    @Transient //skip for db but required for response dto
    private Store store;// after serialization it may be equal to (converted) "store.id" if it is the child reference in the circular dependency
}