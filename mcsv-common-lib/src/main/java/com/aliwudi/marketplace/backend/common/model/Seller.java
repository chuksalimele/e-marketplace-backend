// Seller.java
package com.aliwudi.marketplace.backend.common.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;
// import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // If you use it for bidirectional relationships


@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("sellers")
public class Seller {
    @Id
    private Long id;
    private String name;
    private String email;    
    private String phnoneNumber;// personal phone number - this can be different from the Store phone number which is the office line
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;  
    
    @ToString.Exclude
    @Transient //skip for db but required for response dto    
    private List<Store> stores;


     public Seller(String name, String email) {
         this.name = name;
         this.email = email;
    }    
}