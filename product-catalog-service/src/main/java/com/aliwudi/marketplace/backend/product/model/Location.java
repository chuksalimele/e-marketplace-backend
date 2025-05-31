// Product.java
package com.aliwudi.marketplace.backend.product.model;

import com.aliwudi.marketplace.backend.common.enumeration.LocationCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonBackReference;   // NEW IMPORT
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

@Table("locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    private Long id;
    private String city;
    private String state; //for countrys with state
    private String country; 
    private LocationCategory locationCategory;
    
}