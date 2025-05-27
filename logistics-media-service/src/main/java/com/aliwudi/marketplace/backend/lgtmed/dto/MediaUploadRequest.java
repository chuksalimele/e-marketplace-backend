package com.aliwudi.marketplace.backend.lgtmed.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaUploadRequest {
    private String originalFileName;
    private String fileType; // e.g., image/jpeg
    private String base64Content; // For simulated base64 upload
    private String entityId; // Product ID, User ID, etc.
    private String entityType; // PRODUCT, USER, etc.
}