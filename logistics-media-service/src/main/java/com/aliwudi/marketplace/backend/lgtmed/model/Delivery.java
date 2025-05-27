package com.aliwudi.marketplace.backend.lgtmed.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "deliveries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId; // The order this delivery is for

    @Column(nullable = false, unique = true)
    private String trackingNumber; // Unique tracking number

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    private String currentLocation; // e.g., "Warehouse", "In Transit - Abuja", "Delivered"
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private String recipientName;
    private String recipientAddress;
    private String deliveryAgent; // e.g., "Kwik Delivery", "GIG Logistics"

    public enum DeliveryStatus {
        PENDING, SCHEDULED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED, CANCELLED, RETURNED
    }
}