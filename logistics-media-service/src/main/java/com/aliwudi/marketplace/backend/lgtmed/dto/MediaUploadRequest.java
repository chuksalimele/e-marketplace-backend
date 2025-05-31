package com.aliwudi.marketplace.backend.lgtmed.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaUploadRequest {
    @NotBlank(message = "Asset name cannot be blank")
    private String assetName;

    @NotBlank(message = "File content (base64) cannot be blank")
    private String fileContent; // Base64 encoded file content

    @NotBlank(message = "File type cannot be blank (e.g., image/jpeg, video/mp4)")
    private String fileType;

    @NotBlank(message = "Entity ID cannot be blank")
    private String entityId; // ID of the associated entity (e.g., product ID, user ID)

    @NotBlank(message = "Entity type cannot be blank (e.g., PRODUCT, USER)")
    private String entityType; // Type of the associated entity
}