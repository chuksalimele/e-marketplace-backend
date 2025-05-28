// MessageResponse.java
package com.aliwudi.marketplace.backend.product.dto;

import lombok.AllArgsConstructor; // Lombok for all-args constructor
import lombok.Data; // Lombok for getters/setters
import lombok.NoArgsConstructor; // Lombok for no-args constructor

@Data // From Lombok
@NoArgsConstructor // From Lombok
@AllArgsConstructor // From Lombok
public class MessageResponse {
    private String message;
}