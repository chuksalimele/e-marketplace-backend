package com.aliwudi.marketplace.backend.orderprocessing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockOperationRequest {
    private Long productId;
    private Integer quantity;
}