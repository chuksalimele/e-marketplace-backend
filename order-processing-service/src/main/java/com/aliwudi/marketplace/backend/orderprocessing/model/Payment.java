package com.aliwudi.marketplace.backend.orderprocessing.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId; // Link to the order
    
    @Column(nullable = false)
    private String transactionRef; // Reference from the payment gateway

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status; // PENDING, SUCCESS, FAILED, REFUNDED

    @Column(nullable = false)
    private LocalDateTime paymentDate;

    private String gatewayResponse; // Raw response from payment gateway

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, REFUNDED
    }
}