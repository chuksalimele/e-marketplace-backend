package com.aliwudi.marketplace.backend.product.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.HashSet;
import java.util.Set;
import lombok.Builder;

@Table("stores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    private Long id;
    private String name; // e.g., "Downtown Branch", "Online Warehouse"
    private Long locationId; // with the it we can get its country, state and city
    private String description;
    private String contactInfo;
    private String profileImageUrl;
    private Double rating; // Store-specific rating
    private Long sellerId;

    // Optional: Constructor for convenience
    public Store(String name, Long locationId, Long sellerId) {
        this.name = name;
        this.locationId = locationId;
        this.sellerId = sellerId;
    }
}