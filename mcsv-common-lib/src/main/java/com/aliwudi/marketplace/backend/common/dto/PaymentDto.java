package com.aliwudi.marketplace.backend.common.dto;

import com.aliwudi.marketplace.backend.common.status.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDto {
    private Long id;
    private OrderDto order;
    private String transactionRef;
    private BigDecimal amount;
    private PaymentStatus status; // PENDING, SUCCESS, FAILED, REFUNDED
    private LocalDateTime paymentTime;

    private String gatewayResponse; // Raw response from payment gateway
}