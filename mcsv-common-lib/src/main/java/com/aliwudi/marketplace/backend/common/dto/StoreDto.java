package com.aliwudi.marketplace.backend.common.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.Builder;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreDto {
    private Long id;
    private String name; // e.g., "Downtown Branch", "Online Warehouse"
    private String location; // e.g., "Lagos, Nigeria", "123 Main St, Anytown" - consider splitting into city, street, postal code etc.
    private String address;
    private String phoneNumber;
    private String description;    
    private String profileImageUrl;
    private Double rating; // StoreDto-specific rating
    private SellerDto seller;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
}