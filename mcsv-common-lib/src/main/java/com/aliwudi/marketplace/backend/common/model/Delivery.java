package com.aliwudi.marketplace.backend.common.model;

import com.aliwudi.marketplace.backend.common.status.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;


@ToString
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("deliveries")
public class Delivery {
    @Id
    private Long id; // Using Long for auto-incrementing ID
    private Long orderId; // Links to the Order entity
    
    @ToString.Exclude
    @Transient
    private Order order;
    private String trackingNumber;
    private String recipientName;
    private String recipientAddress;
    private String deliveryAgent; // e.g., "DHL", "FedEx", "Local Courier"
    private DeliveryStatus status; // Enum: PENDING, SHIPPED, IN_TRANSIT, DELIVERED, FAILED, CANCELED
    private String currentLocation; // Latest known location
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate; // Null until delivered
    private String notes; // Any additional notes or updates
    
    @CreatedDate // Automatically populated with creation timestamp
    private LocalDateTime createdAt;
    
    @LastModifiedDate // Automatically populated with last modification timestamp
    private LocalDateTime updatedAt;    

}