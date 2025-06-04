package com.aliwudi.marketplace.backend.lgtmed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryRequest {
    private Long orderId;
    private String recipientName;
    private String recipientAddress;
    private String deliveryAgent; // Optional
    private LocalDateTime estimatedDeliveryDate; // Optional
}