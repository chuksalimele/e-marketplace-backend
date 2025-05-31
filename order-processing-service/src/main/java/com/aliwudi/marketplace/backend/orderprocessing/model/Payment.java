package com.aliwudi.marketplace.backend.orderprocessing.model;

import com.aliwudi.marketplace.backend.common.status.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Table("payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    private Long id;
    private Long userId;
    private Long orderId; 
    private String transactionRef;
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime paymentTime;
    private String gatewayResponse; // Raw response from payment gateway
}