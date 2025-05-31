package com.aliwudi.marketplace.backend.orderprocessing.model;

// Removed: import com.fasterxml.jackson.annotation.JsonBackReference; // No longer needed as User object is removed
import com.fasterxml.jackson.annotation.JsonManagedReference; // Still needed for CartItem
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("carts")
@Data
@NoArgsConstructor 
@AllArgsConstructor 
public class Cart {

    @Id
    private Long id;
    private Long userId; 

    public Cart(Long userId) {
        this.userId = userId;
    }
}