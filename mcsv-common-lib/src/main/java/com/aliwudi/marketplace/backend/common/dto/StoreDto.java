package com.aliwudi.marketplace.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreDto {
    private Long id;
    private String name; // e.g., "Downtown Branch", "Online Warehouse"
    private String location; // e.g., "Lagos, Nigeria", "123 Main St, Anytown" - consider splitting into city, street, postal code etc.
    private String description;
    private String contactInfo;
    private String profileImageUrl;
    private Double rating; // StoreDto-specific rating
    private SellerDto seller;
}