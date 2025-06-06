package com.aliwudi.marketplace.backend.common.model;

import com.aliwudi.marketplace.backend.common.status.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
@Table("payments")
public class Payment {
    @Id
    private Long id;
    private Long userId;
    private Long orderId; // required for db
    @Transient
    private Order order;// skip for db but required for response dto
    private String transactionRef;
    private BigDecimal amount;
    private PaymentStatus status; // PENDING, SUCCESS, FAILED, REFUNDED
    
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;
    
    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;    
    private String gatewayResponse; // Raw response from payment gateway
 
}