package com.aliwudi.marketplace.backend.common.model;

import com.aliwudi.marketplace.backend.common.status.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;



@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("deliveries")
public class Delivery {
    @Id
    private Long id; // Using Long for auto-incrementing ID
    private Long orderId; // Links to the Order entity
    private String trackingNumber;
    private String recipientName;
    private String recipientAddress;
    private String deliveryAgent; // e.g., "DHL", "FedEx", "Local Courier"
    private DeliveryStatus status; // Enum: PENDING, SHIPPED, IN_TRANSIT, DELIVERED, FAILED, CANCELED
    private String currentLocation; // Latest known location
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate; // Null until delivered
    private String notes; // Any additional notes or updates
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}