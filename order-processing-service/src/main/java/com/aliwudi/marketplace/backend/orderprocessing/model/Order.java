package com.aliwudi.marketplace.backend.orderprocessing.model;

import com.aliwudi.marketplace.backend.common.status.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data // Generates getters, setters, equals, hashCode, toString
@NoArgsConstructor // Generates no-argument constructor
// @AllArgsConstructor // Lombok will generate an all-args constructor including userId
// You can remove this annotation if you intend to use the custom constructor below
@ToString(exclude = {"orderItems"}) // Exclude orderItems to prevent StackOverflowError in toString
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- REFACtORED CHANGE ---
    // Replaced direct User object reference with its ID.
    // This userId acts as a foreign key that references the User entity
    // in the separate User Microservice's database.
    @Column(name = "user_id", nullable = false)
    private Long userId; // Changed from 'User user' to 'Long userId'

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = true)
    private String paymentMethod;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    // --- REFACtORED CHANGE ---
    // Updated custom constructor to take userId instead of User object.
    // If you're using Lombok's @AllArgsConstructor, it will automatically adjust.
    // Ensure you align your constructor usage with the Lombok annotation or manual definitions.
    public Order(Long userId, LocalDateTime orderDate, BigDecimal totalAmount, OrderStatus orderStatus,
                 String shippingAddress, String paymentMethod) {
        this.userId = userId;
        this.orderDate = orderDate;
        this.totalAmount = totalAmount;
        this.orderStatus = orderStatus;
        this.shippingAddress = shippingAddress;
        this.paymentMethod = paymentMethod;
        this.orderItems = new ArrayList<>(); // Initialize the list
    }

    @PrePersist
    public void prePersist() {
        if (this.orderDate == null) {
            this.orderDate = LocalDateTime.now();
        }
        if (this.orderStatus == null) {
            this.orderStatus = OrderStatus.PENDING;
        }
    }

    // Helper method to add an OrderItem and maintain bidirectional link
    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }
}