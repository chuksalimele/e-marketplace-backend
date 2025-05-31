// SellerDto.java
package com.aliwudi.marketplace.backend.common.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
// import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // If you use it for bidirectional relationships


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerDto {
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
}