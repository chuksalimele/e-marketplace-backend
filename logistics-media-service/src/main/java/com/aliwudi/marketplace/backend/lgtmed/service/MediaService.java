package com.aliwudi.marketplace.backend.lgtmed.service;


import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;

    // In a real application, this would interact with a cloud storage like S3
    // For now, we simulate storage and generate a dummy URL
    private static final String BASE_STORAGE_URL = "http://assets.ecommerceapp.com/"; // Simulate a CDN or storage URL

    @Transactional
    public MediaAsset uploadMedia(MediaUploadRequest request) {
        log.info("Attempting to upload media: {} for entity: {}", request.getOriginalFileName(), request.getEntityId());
        
        // Simulate file saving and get a unique file name
        String uniqueFileName = UUID.randomUUID().toString() + "_" + request.getOriginalFileName();
        String assetUrl = BASE_STORAGE_URL + uniqueFileName; // Construct the dummy URL

        MediaAsset mediaAsset = MediaAsset.builder()
                .assetName(request.getOriginalFileName())
                .uniqueFileName(uniqueFileName)
                .fileType(request.getFileType())
                .url(assetUrl)
                .entityId(request.getEntityId())
                .entityType(request.getEntityType())
                .uploadDate(LocalDateTime.now())
                .build();

        return mediaAssetRepository.save(mediaAsset);
    }

    @Transactional(readOnly = true)
    public MediaAsset getMediaAssetByUniqueFileName(String uniqueFileName) {
        log.info("Fetching media asset by unique file name: {}", uniqueFileName);
        return mediaAssetRepository.findByUniqueFileName(uniqueFileName)
                .orElseThrow(() -> new RuntimeException("Media asset not found: " + uniqueFileName));
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> getMediaAssetsForEntity(String entityId, String entityType) {
        log.info("Fetching media assets for entityId: {}, entityType: {}", entityId, entityType);
        return mediaAssetRepository.findByEntityIdAndEntityType(entityId, entityType);
    }

    @Transactional
    public void deleteMediaAsset(String uniqueFileName) {
        log.info("Deleting media asset: {}", uniqueFileName);
        MediaAsset mediaAsset = mediaAssetRepository.findByUniqueFileName(uniqueFileName)
                .orElseThrow(() -> new RuntimeException("Media asset not found: " + uniqueFileName));
        // In a real scenario, you'd also delete the actual file from cloud storage here
        mediaAssetRepository.delete(mediaAsset);
    }
}