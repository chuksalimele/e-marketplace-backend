package com.aliwudi.marketplace.backend.orderprocessing.model;

import lombok.AllArgsConstructor; // Will be adjusted by Lombok for new fields
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_items")
@Data // Generates getters, setters, equals, hashCode, toString
@NoArgsConstructor // Generates no-argument constructor
@AllArgsConstructor // Lombok will generate a constructor with all fields (id, order, productId, quantity, priceAtTimeOfOrder)
public class OrderItem {

    @Id
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private BigDecimal priceAtTimeOfOrder; // CRUCIAL: This information is specific to *this* order item
                                          // and must remain here, as product prices can change over time.


}