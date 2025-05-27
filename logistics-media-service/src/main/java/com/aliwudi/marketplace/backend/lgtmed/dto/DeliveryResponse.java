package com.aliwudi.marketplace.backend.lgtmed.dto;

import com.aliwudi.marketplace.backend.lgtmed.model.Delivery.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryResponse {
    private String orderId;
    private String trackingNumber;
    private DeliveryStatus status;
    private String currentLocation;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private String recipientName;
    private String recipientAddress;
    private String deliveryAgent;
    private String message; // For user feedback
}