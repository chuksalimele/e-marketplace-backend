package com.aliwudi.marketplace.backend.common.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryDto {
    private Long id;
    private String orderId; // The order this delivery is for
    private String trackingNumber; // Unique tracking number
    private String status;
    private String currentLocation; // e.g., "Warehouse", "In Transit - Abuja", "Delivered"
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private String recipientName;
    private String recipientAddress;
    private String deliveryAgent; // e.g., "Kwik DeliveryDto", "GIG Logistics"

}