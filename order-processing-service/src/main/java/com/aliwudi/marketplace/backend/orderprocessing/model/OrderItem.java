package com.aliwudi.marketplace.backend.orderprocessing.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data // Generates getters, setters, equals, hashCode, toString
@NoArgsConstructor // Generates no-argument constructor
// @AllArgsConstructor // Lombok will generate a constructor with all fields (id, order, productId, quantity, priceAtTimeOfOrder)
@ToString(exclude = {"order"}) // Exclude order to prevent StackOverflowError in toString
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many-to-One relationship with Order (remains within the same service)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // --- REFACtORED CHANGE ---
    // Replaced direct Product object reference with its ID.
    // This productId acts as a foreign key that references the Product entity
    // in the separate Product Catalog Microservice's database.
    @Column(name = "product_id", nullable = false)
    private Long productId; // Changed from 'Product product' to 'Long productId'

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtTimeOfOrder; // CRUCIAL: This information is specific to *this* order item
                                          // and must remain here, as product prices can change over time.

    // --- REFACtORED CHANGE ---
    // Updated constructor to take productId instead of Product object.
    // If you're using Lombok's @AllArgsConstructor, it will automatically adjust.
    public OrderItem(Order order, Long productId, Integer quantity, BigDecimal priceAtTimeOfOrder) {
        this.order = order;
        this.productId = productId;
        this.quantity = quantity;
        this.priceAtTimeOfOrder = priceAtTimeOfOrder;
    }

    // Lombok's @Data will generate getters and setters for all fields.
    // If you had any specific logic in custom 'getProduct()' or 'setProduct()' methods,
    // that logic would need to be moved to your OrderService (or a dedicated service layer)
    // and adapted to use productId and potentially call the Product Catalog Service.
}