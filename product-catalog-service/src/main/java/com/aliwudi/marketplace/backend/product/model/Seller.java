// Seller.java
package com.aliwudi.marketplace.backend.product.model;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode; // Assuming you're using this for @EqualsAndHashCode.Exclude
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonManagedReference;   // NEW IMPORT
import java.time.LocalDateTime;
import lombok.Builder;
// import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // If you use it for bidirectional relationships
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;


@Table("sellers")
@Data
@NoArgsConstructor
@AllArgsConstructor 
@Builder
public class Seller {

    @Id
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    

     public Seller(String name, String email) {
         this.name = name;
         this.email = email;
    }
}