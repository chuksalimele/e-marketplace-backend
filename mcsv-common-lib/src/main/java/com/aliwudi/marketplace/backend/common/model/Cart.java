package com.aliwudi.marketplace.backend.common.model;

// Removed: import com.fasterxml.jackson.annotation.JsonBackReference; // No longer needed as User object is removed
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor; // Will be adjusted for the new constructor
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;


@Data
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("carts")
public class Cart {

    @Id
    private Long id;    
    private Long userId; // required for db 
    
    @Transient
    private User user;  //skip for db but required for response
    
    @Transient
    private List<CartItem> items; //skip for db but required for response
    
    private BigDecimal totalAmount;
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;
    
    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;    
}