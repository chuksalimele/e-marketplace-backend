package com.aliwudi.marketplace.backend.common.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.Builder;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("stores")
public class Store {
    private Long id;
    private String name; 
    private Long sellerId;// required for db
    
    @Transient
    private Seller seller;// skip for db but required for response dto  
    
    private Long locationId; // required for db
    @Transient
    private Location Location;// skip for db but required for response dto   
    
    @Transient
    private List<Product> products;// skip for db but required for response dto   
    private String address;
    private String phoneNumber;
    private String description;    
    private String profileImageUrl;
    private Double rating; // Store-specific rating
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
    
    public Store(String name, Long locationId, Long sellerId) {
        this.name = name;
        this.locationId = locationId;
        this.sellerId = sellerId;
    }    
}