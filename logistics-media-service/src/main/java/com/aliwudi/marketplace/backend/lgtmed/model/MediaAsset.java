package com.aliwudi.marketplace.backend.lgtmed.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("media_assets") // Ensure your table name is correct
public class MediaAsset {
    @Id
    private Long id; // Using Long for auto-incrementing ID
    private String assetName;
    private String uniqueFileName; // Stored filename (e.g., S3 key, generated UUID)
    private String url; // Public URL to access the asset
    private String fileType; // e.g., image/jpeg, video/mp4
    private String entityId; // ID of the associated entity (e.g., product ID, user ID)
    private String entityType; // Type of the associated entity (e.g., PRODUCT, USER, REVIEW)
    private LocalDateTime uploadTime;
    private Long fileSize; // Optional: Store file size
    private String storagePath; // Optional: Internal path in storage system
    private String uploadedBy; // Optional: User who uploaded this asset
}