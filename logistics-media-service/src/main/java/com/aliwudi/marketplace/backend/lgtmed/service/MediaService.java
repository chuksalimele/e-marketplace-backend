package com.aliwudi.marketplace.backend.lgtmed.service;

import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.repository.MediaAssetRepository; // Assumed to be a Reactive Repository
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Keep for reactive transaction management if configured
import reactor.core.publisher.Mono; // NEW: Import Mono
import reactor.core.publisher.Flux; // NEW: Import Flux

import java.time.LocalDateTime;
// Removed java.util.List and java.util.Optional imports as they are replaced by Flux and Mono
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;

    // In a real application, this would interact with a cloud storage like S3
    // For now, we simulate storage and generate a dummy URL
    private static final String BASE_STORAGE_URL = "http://assets.ecommerceapp.com/"; // Simulate a CDN or storage URL

    @Transactional // Apply if you have a reactive transaction manager (e.g., for R2DBC)
    public Mono<MediaAsset> uploadMedia(MediaUploadRequest request) {
        log.info("Attempting to upload media: {} for entity: {}", request.getOriginalFileName(), request.getEntityId());

        String uniqueFileName = UUID.randomUUID().toString() + "_" + request.getOriginalFileName();
        String assetUrl = BASE_STORAGE_URL + uniqueFileName;

        MediaAsset mediaAsset = MediaAsset.builder()
                .assetName(request.getOriginalFileName())
                .uniqueFileName(uniqueFileName)
                .fileType(request.getFileType())
                .url(assetUrl)
                .entityId(request.getEntityId())
                .entityType(request.getEntityType())
                .uploadDate(LocalDateTime.now())
                .build();

        return mediaAssetRepository.save(mediaAsset) // Returns Mono<MediaAsset>
                .doOnSuccess(savedAsset -> log.info("Media asset uploaded successfully: {}", savedAsset.getUniqueFileName()))
                .onErrorResume(e -> {
                    log.error("Failed to upload media asset: {}", request.getOriginalFileName(), e);
                    return Mono.error(new RuntimeException("Failed to upload media asset: " + e.getMessage()));
                });
    }

    @Transactional(readOnly = true)
    public Mono<MediaAsset> getMediaAssetByUniqueFileName(String uniqueFileName) {
        log.info("Fetching media asset by unique file name: {}", uniqueFileName);
        // Using switchIfEmpty to handle the case where the asset is not found
        return mediaAssetRepository.findByUniqueFileName(uniqueFileName) // Assumed to return Mono<MediaAsset>
                .switchIfEmpty(Mono.error(new RuntimeException("Media asset not found: " + uniqueFileName)))
                .doOnSuccess(asset -> log.info("Found media asset: {}", uniqueFileName));
    }

    @Transactional(readOnly = true)
    public Flux<MediaAsset> getMediaAssetsForEntity(String entityId,
            String entityType,
            Long offset, 
            Integer limit) { 
        log.info("Fetching media assets for entityId: {}, entityType: {} with offset {} and limit {}", entityId, entityType, offset, limit);
        return mediaAssetRepository.findByEntityIdAndEntityType(entityId, entityType, offset, limit)
                .doOnComplete(() -> log.info("Finished fetching media assets for entity: {}", entityId))
                .doOnError(e -> log.error("Error fetching media assets for entity: {}", entityId, e));
    }

    @Transactional
    public Mono<Void> deleteMediaAsset(String uniqueFileName) {
        log.info("Attempting to delete media asset: {}", uniqueFileName);
        return mediaAssetRepository.findByUniqueFileName(uniqueFileName)
                .switchIfEmpty(Mono.error(new RuntimeException("Media asset not found for deletion: " + uniqueFileName)))
                .flatMap(mediaAsset -> {
                    log.info("Deleting media asset from repository: {}", uniqueFileName);
                    // In a real scenario, you'd also interact with cloud storage reactively here
                    // e.g., cloudStorageService.deleteFile(mediaAsset.getUrl()).then(mediaAssetRepository.delete(mediaAsset));
                    return mediaAssetRepository.delete(mediaAsset); // Returns Mono<Void>
                })
                .doOnSuccess(v -> log.info("Media asset deleted successfully: {}", uniqueFileName))
                .onErrorResume(e -> {
                    log.error("Failed to delete media asset: {}", uniqueFileName, e);
                    return Mono.error(new RuntimeException("Failed to delete media asset: " + e.getMessage()));
                });
    }
}
