package com.aliwudi.marketplace.backend.common.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data // Generates getters, setters, equals, hashCode, toString
@NoArgsConstructor // Generates no-argument constructor
// @AllArgsConstructor // Lombok will generate an all-args constructor including userId
public class OrderDto {

    private Long id;
    private UserDto user; 
    private LocalDateTime orderDate;
    private BigDecimal totalAmount;
    private OrderStatusDto orderStatus;
    private String shippingAddress;
    private String paymentMethod;
    private List<OrderItemDto> orderItems = new ArrayList<>();

}