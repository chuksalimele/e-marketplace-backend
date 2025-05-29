package com.aliwudi.marketplace.backend.common.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto {
    private Long id;
    private OrderDto order;
    private String transactionRef;
    private BigDecimal amount;
    private PaymentStatus status; // PENDING, SUCCESS, FAILED, REFUNDED
    private LocalDateTime paymentDate;

    private String gatewayResponse; // Raw response from payment gateway

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, REFUNDED
    }
}