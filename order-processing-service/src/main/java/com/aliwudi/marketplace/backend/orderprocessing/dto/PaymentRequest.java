package com.aliwudi.marketplace.backend.orderprocessing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    private String orderId;
    private BigDecimal amount;
    // In a real system, you'd likely pass product IDs and quantities
    // For this example, we'll include a placeholder for inventory updates
    private String productIdForInventory; // Temporary for connecting with inventory service directly
    private Integer quantityForInventory; // Temporary for connecting with inventory service directly
}