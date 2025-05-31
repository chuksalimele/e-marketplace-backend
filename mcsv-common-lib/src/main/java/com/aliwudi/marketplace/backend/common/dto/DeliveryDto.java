package com.aliwudi.marketplace.backend.common.dto;

import com.aliwudi.marketplace.backend.common.status.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryDto {

    private Long id; // Using Long for auto-incrementing ID
    private String orderId; // Links to the Order entity
    private String trackingNumber;
    private String recipientName;
    private String recipientAddress;
    private String deliveryAgent; // e.g., "DHL", "FedEx", "Local Courier", "Kwik DeliveryDto", "GIG Logistics"
    private DeliveryStatus status; // Enum: PENDING, SHIPPED, IN_TRANSIT, DELIVERED, FAILED, CANCELED
    private String currentLocation; // Latest known location -  e.g., "Warehouse", "In Transit - Abuja", "Delivered"
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate; // Null until delivered
    private String notes; // Any additional notes or updates
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    

}