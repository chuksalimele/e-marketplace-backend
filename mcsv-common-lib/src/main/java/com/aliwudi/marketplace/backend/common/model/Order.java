package com.aliwudi.marketplace.backend.common.model;

import com.aliwudi.marketplace.backend.common.status.OrderStatus;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
@Table("orders")
public class Order {

    @Id
    private Long id;   
    private Long userId; // required for db 
    @Transient
    private User user; // skip for db but required for response dto
    private LocalDateTime orderTime;    
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;
    private String shippingAddress;
    private String paymentMethod;
    @Transient
    private List<OrderItem> items;// skip for db but required for response dto
    
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;
    
    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;    

}