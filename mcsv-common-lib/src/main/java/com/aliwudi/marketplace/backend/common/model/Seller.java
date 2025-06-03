// Seller.java
package com.aliwudi.marketplace.backend.common.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
// import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // If you use it for bidirectional relationships


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("sellers")
public class Seller {
    @Id
    private Long id;
    private String name;
    private String email;
    private String phnoneNumber;// personal phone number - this can be different from the Store phone number which is the office line
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    


     public Seller(String name, String email) {
         this.name = name;
         this.email = email;
    }    
}