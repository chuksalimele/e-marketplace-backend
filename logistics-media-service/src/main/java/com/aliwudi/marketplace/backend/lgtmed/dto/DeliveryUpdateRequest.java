package com.aliwudi.marketplace.backend.lgtmed.dto;

import com.aliwudi.marketplace.backend.lgtmed.model.Delivery.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryUpdateRequest {
    private String trackingNumber;
    private DeliveryStatus newStatus;
    private String currentLocation; // Optional
    private String notes; // e.g., "Customer not available"
}