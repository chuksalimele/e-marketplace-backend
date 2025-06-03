package com.aliwudi.marketplace.backend.common.model;

// Removed: import com.fasterxml.jackson.annotation.JsonBackReference; // No longer needed as User object is removed
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;


@Data
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Table("carts")
public class Cart {

    @Id
    private Long id;    
    private Long userId; // required for db 
    
    @Transient
    private User user;  //skip for db but required for response
    
    @Transient
    private Set<CartItem> items; //skip for db but required for response
    
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
}