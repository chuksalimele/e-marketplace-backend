package com.aliwudi.marketplace.backend.orderprocessing.model;

import com.aliwudi.marketplace.backend.common.status.OrderStatus;
import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("orders")
@Data // Generates getters, setters, equals, hashCode, toString
@NoArgsConstructor 
@AllArgsConstructor
public class Order {

    @Id
    private Long id;
    private Long userId; 
    private LocalDateTime orderTime;
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;
    private String shippingAddress;
    private String paymentMethod;
}