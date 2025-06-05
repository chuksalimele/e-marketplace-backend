package com.aliwudi.marketplace.backend.common.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
@Table("media_assets")
public class MediaAsset {
    @Id
    private Long id; // Using Long for auto-incrementing ID
    private String assetName; // Original file name
    private String uniqueFileName; // Stored file name / key in storage
    private String fileType; // e.g., image/jpeg, image/png
    private String url; // Publicly accessible URL for the asset
    private String entityId; // e.g., productId, userId, categoryId
    private String entityType; // e.g., "PRODUCT", "USER", "CATEGORY" - helps categorize assets
    private LocalDateTime uploadTime;
    private Long fileSize; // Optional: Store file size
    private String storagePath; // Optional: Internal path in storage system
    private String uploadedBy; // Optional: User who uploaded this asset    
}
