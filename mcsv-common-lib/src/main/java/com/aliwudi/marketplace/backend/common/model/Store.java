package com.aliwudi.marketplace.backend.common.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.Builder;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
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
@Table("stores")
public class Store {
    private Long id;
    private String name; 
    private Long sellerId;// required for db
    
    @ToString.Exclude
    @Transient
    private Seller seller;// skip for db but required for response dto  
    
    private Long locationId; // required for db
    
    @ToString.Exclude
    @Transient
    private Location Location;// skip for db but required for response dto   
    
    @ToString.Exclude
    @Transient
    private List<Product> products;// skip for db but required for response dto 
    
    private String address;
    private String phoneNumber;
    private String description;    
    private String profileImageUrl;
    private Double rating; // Store-specific rating
    
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;
    
    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;      
    
    public Store(String name, Long locationId, Long sellerId) {
        this.name = name;
        this.locationId = locationId;
        this.sellerId = sellerId;
    }    
}