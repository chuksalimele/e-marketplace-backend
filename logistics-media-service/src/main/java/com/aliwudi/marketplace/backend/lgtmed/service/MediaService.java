package com.aliwudi.marketplace.backend.lgtmed.service;

import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.exception.InvalidMediaDataException;
import com.aliwudi.marketplace.backend.lgtmed.exception.MediaAssetNotFoundException;
import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.repository.MediaAssetRepository; // Corrected import
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Import Slf4j for logging
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable; // Import for pagination

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@Service
@RequiredArgsConstructor
@Slf4j // Add Slf4j for logging
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository; // Corrected repository name

    // In a real application, you'd inject a cloud storage client (e.g., S3Client, GcsClient)
    // For demonstration, we'll simulate storage operations.
    // private final S3Client s3Client; // Example

    /**
     * Uploads a new media asset.
     *
     * @param request The MediaUploadRequest containing asset details and content.
     * @return A Mono emitting the created MediaAsset.
     * @throws InvalidMediaDataException if file type is unsupported or content is invalid.
     */
    public Mono<MediaAsset> uploadMedia(MediaUploadRequest request) {
        // In a real scenario, convert fileContent (Base64) to bytes and upload to cloud storage
        // This is a simplified example.
        return Mono.just(request)
                .flatMap(req -> {
                    // Simulate file storage and URL generation
                    String uniqueFileName = UUID.randomUUID().toString() + "_" + req.getAssetName().replaceAll("\\s+", "_");
                    String simulatedUrl = "https://your-cloud-storage.com/media/" + uniqueFileName;

                    // Validate file type (basic example)
                    if (!isValidFileType(req.getFileType())) {
                        return Mono.error(new InvalidMediaDataException(ApiResponseMessages.UNSUPPORTED_FILE_TYPE + req.getFileType()));
                    }

                    // Decode Base64 content (for potential validation or processing, not actually stored here)
                    try {
                        byte[] fileBytes = Base64.getDecoder().decode(req.getFileContent());
                        // long fileSize = fileBytes.length; // You might use this
                        // Simulate upload to S3/GCS:
                        // return Mono.fromCallable(() -> s3Client.putObject(PutObjectRequest.builder()...))
                        //            .thenReturn(new MediaAsset(...))

                    } catch (IllegalArgumentException e) {
                        return Mono.error(new InvalidMediaDataException(ApiResponseMessages.INVALID_BASE64_CONTENT));
                    }

                    MediaAsset mediaAsset = MediaAsset.builder()
                            .assetName(req.getAssetName())
                            .uniqueFileName(uniqueFileName)
                            .url(simulatedUrl)
                            .fileType(req.getFileType())
                            .entityId(req.getEntityId())
                            .entityType(req.getEntityType())
                            .uploadTime(LocalDateTime.now())
                            // .fileSize(fileSize) // If calculated
                            .build();

                    return mediaAssetRepository.save(mediaAsset); // Corrected repository name
                })
                .onErrorResume(e -> {
                    log.error("Error in uploadMedia service: {}", e.getMessage());
                    if (e instanceof InvalidMediaDataException) {
                        return Mono.error(e); // Re-throw specific exception
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_UPLOADING_MEDIA + ": " + e.getMessage()));
                });
    }

    /**
     * Retrieves a media asset by its unique file name.
     *
     * @param uniqueFileName The unique file name of the media asset.
     * @return A Mono emitting the MediaAsset if found.
     * @throws MediaAssetNotFoundException if no media asset is found for the given unique file name.
     */
    public Mono<MediaAsset> getMediaAssetByUniqueFileName(String uniqueFileName) {
        return mediaAssetRepository.findByUniqueFileName(uniqueFileName) // Corrected repository name
                .switchIfEmpty(Mono.error(new MediaAssetNotFoundException(ApiResponseMessages.MEDIA_NOT_FOUND + uniqueFileName)))
                .onErrorResume(e -> {
                    log.error("Error in getMediaAssetByUniqueFileName service: {}", e.getMessage());
                    if (e instanceof MediaAssetNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_MEDIA + ": " + e.getMessage()));
                });
    }

    /**
     * Retrieves media assets for a specific entity with pagination.
     *
     * @param entityId The ID of the entity.
     * @param entityType The type of the entity.
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records.
     */
    public Flux<MediaAsset> getMediaAssetsForEntity(String entityId, String entityType, Pageable pageable) {
        return mediaAssetRepository.findByEntityIdAndEntityType(entityId, entityType, pageable) // Corrected repository name
                .onErrorResume(e -> {
                    log.error("Error in getMediaAssetsForEntity service for entity {} type {}: {}", entityId, entityType, e.getMessage());
                    return Flux.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_ENTITY_MEDIA + ": " + e.getMessage()));
                });
    }

    /**
     * Counts media assets for a specific entity.
     *
     * @param entityId The ID of the entity.
     * @param entityType The type of the entity.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countMediaAssetsForEntity(String entityId, String entityType) {
        return mediaAssetRepository.countByEntityIdAndEntityType(entityId, entityType) // Corrected repository name
                .onErrorResume(e -> {
                    log.error("Error in countMediaAssetsForEntity service for entity {} type {}: {}", entityId, entityType, e.getMessage());
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_COUNTING_ENTITY_MEDIA + ": " + e.getMessage()));
                });
    }

    /**
     * Deletes a media asset by its unique file name.
     *
     * @param uniqueFileName The unique file name of the media asset to delete.
     * @return A Mono<Void> indicating completion.
     * @throws MediaAssetNotFoundException if the media asset is not found.
     */
    public Mono<Void> deleteMediaAsset(String uniqueFileName) {
        // In a real scenario, you would delete from cloud storage first, then from DB
        return mediaAssetRepository.findByUniqueFileName(uniqueFileName) // Corrected repository name
                .switchIfEmpty(Mono.error(new MediaAssetNotFoundException(ApiResponseMessages.MEDIA_NOT_FOUND_FOR_DELETE + uniqueFileName)))
                .flatMap(asset -> {
                    // Simulate deletion from cloud storage:
                    // return Mono.fromCallable(() -> s3Client.deleteObject(DeleteObjectRequest.builder()...))
                    //            .then(mediaAssetRepository.delete(asset)); // Corrected repository name
                    log.info("Simulating deletion of {} from cloud storage.", asset.getUrl());
                    return mediaAssetRepository.delete(asset); // Corrected repository name
                })
                .then() // Ensure Mono<Void> is returned
                .onErrorResume(e -> {
                    log.error("Error in deleteMediaAsset service: {}", e.getMessage());
                    if (e instanceof MediaAssetNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_DELETING_MEDIA + ": " + e.getMessage()));
                });
    }

    // --- NEW: Implementations for all MediaAssetRepository methods ---

    /**
     * Retrieves all media assets with pagination.
     *
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records.
     */
    public Flux<MediaAsset> findAllMediaAssets(Pageable pageable) {
        log.info("Finding all media assets with pagination: {}", pageable);
        return mediaAssetRepository.findAllBy(pageable); // Corrected repository name
    }

    /**
     * Finds media assets by their entity type with pagination.
     *
     * @param entityType The type of the entity.
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records.
     */
    public Flux<MediaAsset> findMediaAssetsByEntityType(String entityType, Pageable pageable) {
        log.info("Finding media assets by entity type: {} with pagination: {}", entityType, pageable);
        return mediaAssetRepository.findByEntityType(entityType, pageable); // Corrected repository name
    }

    /**
     * Finds media assets by their file type with pagination.
     *
     * @param fileType The type of the file (e.g., "image/jpeg", "video/mp4").
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records.
     */
    public Flux<MediaAsset> findMediaAssetsByFileType(String fileType, Pageable pageable) {
        log.info("Finding media assets by file type: {} with pagination: {}", fileType, pageable);
        return mediaAssetRepository.findByFileType(fileType, pageable); // Corrected repository name
    }

    /**
     * Finds media assets whose asset name contains the specified string (case-insensitive), with pagination.
     *
     * @param assetName The asset name string to search for.
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records.
     */
    public Flux<MediaAsset> findMediaAssetsByAssetNameContaining(String assetName, Pageable pageable) {
        log.info("Finding media assets by asset name containing '{}' with pagination: {}", assetName, pageable);
        return mediaAssetRepository.findByAssetNameContainingIgnoreCase(assetName, pageable); // Corrected repository name
    }

    /**
     * Counts media assets by their entity type.
     *
     * @param entityType The type of the entity.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countMediaAssetsByEntityType(String entityType) {
        log.info("Counting media assets by entity type: {}", entityType);
        return mediaAssetRepository.countByEntityType(entityType); // Corrected repository name
    }

    /**
     * Counts media assets by their file type.
     *
     * @param fileType The type of the file.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countMediaAssetsByFileType(String fileType) {
        log.info("Counting media assets by file type: {}", fileType);
        return mediaAssetRepository.countByFileType(fileType); // Corrected repository name
    }

    /**
     * Counts media assets whose asset name contains the specified string (case-insensitive).
     *
     * @param assetName The asset name string to search for.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countMediaAssetsByAssetNameContaining(String assetName) {
        log.info("Counting media assets by asset name containing '{}'", assetName);
        return mediaAssetRepository.countByAssetNameContainingIgnoreCase(assetName); // Corrected repository name
    }

    /**
     * Checks if a media asset with a given unique file name exists.
     *
     * @param uniqueFileName The unique file name.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> existsByUniqueFileName(String uniqueFileName) {
        log.info("Checking if media asset exists for unique file name: {}", uniqueFileName);
        return mediaAssetRepository.existsByUniqueFileName(uniqueFileName); // Corrected repository name
    }

    // Helper for basic file type validation
    private boolean isValidFileType(String fileType) {
        return fileType != null &&
               (fileType.startsWith("image/") ||
                fileType.startsWith("video/") ||
                fileType.startsWith("audio/") ||
                fileType.equals("application/pdf")); // Example types
    }
}