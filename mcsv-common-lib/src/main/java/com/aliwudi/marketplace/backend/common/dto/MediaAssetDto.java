package com.aliwudi.marketplace.backend.common.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaAssetDto {
    private Long id;
    private String assetName; // Original file name
    private String uniqueFileName; // Stored file name / key in storage
    private String fileType; // e.g., image/jpeg, image/png
    private String url; // Publicly accessible URL for the asset
    private String entityId; // e.g., productId, userId, categoryId
    private String entityType; // e.g., "PRODUCT", "USER", "CATEGORY" - helps categorize assets
    private LocalDateTime uploadDate;
}