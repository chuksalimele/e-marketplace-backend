// ProductRequest.java
package com.aliwudi.marketplace.backend.orderprocessing.dto;

import com.aliwudi.marketplace.backend.orderprocessing.service.OrderService;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;



@Data // Generates getters and setters for this DTO
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotNull(message = "User ID cannot be null")
    private Long userId;
    
    @NotNull(message = "Items cannot be null")
    @Size(min = 1, message = "At least one item must be included in the order")
    private List<OrderService.OrderItemRequest> items;

    @NotBlank(message = "Shipping address cannot be blank")
    private String shippingAddress;


    @NotBlank(message = "Payment method cannot be blank")
    @Size(max = 50, message = "Payment method cannot exceed 50 characters")     
    private String paymentMethod;
}
