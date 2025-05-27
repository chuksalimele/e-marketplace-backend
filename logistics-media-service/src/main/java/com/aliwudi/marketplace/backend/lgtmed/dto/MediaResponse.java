package com.ecommerce.logisticsmedia.media.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaResponse {
    private String assetId; // Unique ID of the asset record
    private String assetName;
    private String url;
    private String fileType;
    private String entityId;
    private String entityType;
    private LocalDateTime uploadDate;
    private String message;
}