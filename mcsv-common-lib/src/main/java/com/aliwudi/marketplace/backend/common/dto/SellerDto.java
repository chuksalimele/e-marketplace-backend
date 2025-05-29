// SellerDto.java
package com.aliwudi.marketplace.backend.common.dto;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode; // Assuming you're using this for @EqualsAndHashCode.Exclude
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonManagedReference;   // NEW IMPORT
// import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // If you use it for bidirectional relationships


@Data
@NoArgsConstructor
@AllArgsConstructor // This will now generate a constructor with all 8 fields (id, name, email, location, etc.)
public class SellerDto {
    private Long id;
    private String name;
    private String email;
}