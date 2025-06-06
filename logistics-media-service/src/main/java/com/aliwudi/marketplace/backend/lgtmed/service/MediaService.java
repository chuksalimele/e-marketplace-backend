package com.aliwudi.marketplace.backend.lgtmed.service;

import com.aliwudi.marketplace.backend.common.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.common.exception.InvalidMediaDataException;
import com.aliwudi.marketplace.backend.common.exception.MediaAssetNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.lgtmed.repository.MediaRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaRepository mediaRepository;

    // In a real application, you'd inject a cloud storage client (e.g., S3Client, GcsClient)
    // For demonstration, we'll simulate storage operations.
    // private final S3Client s3Client; // Example

    // IMPORTANT: This prepareDto method is moved from the controller
    // and kept *exactly* as provided by you. It is now a private helper method
    // within the service to enrich the entities before they are returned.
    /**
     * Helper method to map MediaAsset entity to MediaAsset DTO for public
     * exposure.
     */
    private Mono<MediaAsset> prepareDto(MediaAsset asset) {
        if (asset == null) {
            return Mono.empty(); // Changed from null to Mono.empty() for reactive flow consistency
        }

        MediaAsset media = MediaAsset.builder()
                .id(asset.getId())
                .assetName(asset.getAssetName())
                .uniqueFileName(asset.getUniqueFileName())
                .url(asset.getUrl())
                .fileType(asset.getFileType())
                .entityId(asset.getEntityId())
                .entityType(asset.getEntityType())
                .uploadTime(asset.getUploadTime())
                .build();

        return Mono.just(media);
    }

    /**
     * Uploads a new media asset.
     *
     * @param request The MediaUploadRequest containing asset details and content.
     * @return A Mono emitting the created MediaAsset (enriched).
     * @throws InvalidMediaDataException if file type is unsupported, content is invalid, or a media asset with the same unique file name already exists.
     */
    public Mono<MediaAsset> uploadMedia(MediaUploadRequest request) {
        log.info("Attempting to upload media asset: {}", request.getAssetName());

        // Validate file type
        if (!isValidFileType(request.getFileType())) {
            log.warn("Unsupported file type for upload: {}", request.getFileType());
            return Mono.error(new InvalidMediaDataException(ApiResponseMessages.UNSUPPORTED_FILE_TYPE + request.getFileType()));
        }

        // Decode Base64 content (for potential validation or processing, not actually stored here)
        byte[] fileBytes;
        try {
            fileBytes = Base64.getDecoder().decode(request.getFileContent());
            // long fileSize = fileBytes.length; // You might use this
        } catch (IllegalArgumentException e) {
            log.warn("Invalid Base64 content for upload: {}", e.getMessage());
            return Mono.error(new InvalidMediaDataException(ApiResponseMessages.INVALID_BASE64_CONTENT));
        }

        // Generate unique file name and simulated URL
        String uniqueFileName = UUID.randomUUID().toString() + "_" + request.getAssetName().replaceAll("\\s+", "_");
        String simulatedUrl = "https://your-cloud-storage.com/media/" + uniqueFileName;

        // Check if a media asset with this generated unique file name already exists (though UUID should prevent this)
        return mediaRepository.existsByUniqueFileName(uniqueFileName)
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Generated unique file name already exists: {}", uniqueFileName);
                        return Mono.error(new InvalidMediaDataException(ApiResponseMessages.DUPLICATE_MEDIA_UNIQUE_FILE_NAME));
                    }

                    MediaAsset mediaAsset = MediaAsset.builder()
                            .assetName(request.getAssetName())
                            .uniqueFileName(uniqueFileName)
                            .url(simulatedUrl)
                            .fileType(request.getFileType())
                            .entityId(request.getEntityId())
                            .entityType(request.getEntityType())
                            .uploadTime(LocalDateTime.now())
                            // .fileSize(fileSize) // If calculated
                            .build();

                    return mediaRepository.save(mediaAsset);
                })
                .flatMap(this::prepareDto) // Enrich the created media asset
                .doOnSuccess(asset -> log.info("Media asset uploaded successfully: {}", asset.getUniqueFileName()))
                .doOnError(e -> log.error("Error uploading media asset {}: {}", request.getAssetName(), e.getMessage(), e));
    }

    /**
     * Retrieves a media asset by its unique file name, enriching it.
     *
     * @param uniqueFileName The unique file name of the media asset.
     * @return A Mono emitting the MediaAsset if found (enriched).
     * @throws MediaAssetNotFoundException if no media asset is found for the given unique file name.
     */
    public Mono<MediaAsset> getMediaAssetByUniqueFileName(String uniqueFileName) {
        log.info("Retrieving media asset by unique file name: {}", uniqueFileName);
        return mediaRepository.findByUniqueFileName(uniqueFileName)
                .switchIfEmpty(Mono.error(new MediaAssetNotFoundException(ApiResponseMessages.MEDIA_NOT_FOUND + uniqueFileName)))
                .flatMap(this::prepareDto) // Enrich the media asset
                .doOnSuccess(asset -> log.info("Media asset retrieved successfully: {}", asset.getUniqueFileName()))
                .doOnError(e -> log.error("Error retrieving media asset {}: {}", uniqueFileName, e.getMessage(), e));
    }

    /**
     * Retrieves media assets for a specific entity with pagination, enriching each.
     *
     * @param entityId The ID of the entity.
     * @param entityType The type of the entity.
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records (enriched).
     */
    public Flux<MediaAsset> getMediaAssetsForEntity(String entityId, String entityType, Pageable pageable) {
        log.info("Retrieving media assets for entity ID {} and type {} with pagination: {}", entityId, entityType, pageable);
        return mediaRepository.findByEntityIdAndEntityType(entityId, entityType, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving media assets for entity ID {} and type {} for page {} with size {}.", entityId, entityType, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving media assets for entity {} type {}: {}", entityId, entityType, e.getMessage(), e));
    }

    /**
     * Counts media assets for a specific entity.
     *
     * @param entityId The ID of the entity.
     * @param entityType The type of the entity.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countMediaAssetsForEntity(String entityId, String entityType) {
        log.info("Counting media assets for entity ID {} and type {}", entityId, entityType);
        return mediaRepository.countByEntityIdAndEntityType(entityId, entityType)
                .doOnSuccess(count -> log.info("Total media asset count for entity ID {} type {}: {}", entityId, entityType, count))
                .doOnError(e -> log.error("Error counting media assets for entity {} type {}: {}", entityId, entityType, e.getMessage(), e));
    }

    /**
     * Deletes a media asset by its unique file name.
     * This operation is transactional.
     *
     * @param uniqueFileName The unique file name of the media asset to delete.
     * @return A Mono<Void> indicating completion.
     * @throws MediaAssetNotFoundException if the media asset is not found.
     */
    public Mono<Void> deleteMediaAsset(String uniqueFileName) {
        log.info("Attempting to delete media asset with unique file name: {}", uniqueFileName);
        // In a real scenario, you would delete from cloud storage first, then from DB
        return mediaRepository.findByUniqueFileName(uniqueFileName)
                .switchIfEmpty(Mono.error(new MediaAssetNotFoundException(ApiResponseMessages.MEDIA_NOT_FOUND_FOR_DELETE + uniqueFileName)))
                .flatMap(asset -> {
                    // Simulate deletion from cloud storage:
                    // return Mono.fromCallable(() -> s3Client.deleteObject(DeleteObjectRequest.builder()...))
                    //            .then(mediaRepository.delete(asset));
                    log.info("Simulating deletion of {} from cloud storage.", asset.getUrl());
                    return mediaRepository.delete(asset);
                })
                .then() // Ensure Mono<Void> is returned
                .doOnSuccess(v -> log.info("Media asset deleted successfully: {}", uniqueFileName))
                .doOnError(e -> log.error("Error deleting media asset {}: {}", uniqueFileName, e.getMessage(), e));
    }

    // --- NEW: Implementations for all MediaAssetRepository methods (with logging and error handling) ---

    /**
     * Retrieves all media assets with pagination, enriching each.
     *
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records (enriched).
     */
    public Flux<MediaAsset> findAllMediaAssets(Pageable pageable) {
        log.info("Finding all media assets with pagination: {}", pageable);
        return mediaRepository.findAllBy(pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving all media assets for page {} with size {}.", pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving all media assets: {}", e.getMessage(), e));
    }

    /**
     * Finds media assets by their entity type with pagination, enriching each.
     *
     * @param entityType The type of the entity.
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records (enriched).
     */
    public Flux<MediaAsset> findMediaAssetsByEntityType(String entityType, Pageable pageable) {
        log.info("Finding media assets by entity type: {} with pagination: {}", entityType, pageable);
        return mediaRepository.findByEntityType(entityType, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving media assets by entity type '{}' for page {} with size {}.", entityType, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving media assets by entity type {}: {}", entityType, e.getMessage(), e));
    }

    /**
     * Finds media assets by their file type with pagination, enriching each.
     *
     * @param fileType The type of the file (e.g., "image/jpeg", "video/mp4").
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records (enriched).
     */
    public Flux<MediaAsset> findMediaAssetsByFileType(String fileType, Pageable pageable) {
        log.info("Finding media assets by file type: {} with pagination: {}", fileType, pageable);
        return mediaRepository.findByFileType(fileType, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving media assets by file type '{}' for page {} with size {}.", fileType, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving media assets by file type {}: {}", fileType, e.getMessage(), e));
    }

    /**
     * Finds media assets whose asset name contains the specified string (case-insensitive), with pagination, enriching each.
     *
     * @param assetName The asset name string to search for.
     * @param pageable Pagination information.
     * @return A Flux of MediaAsset records (enriched).
     */
    public Flux<MediaAsset> findMediaAssetsByAssetNameContaining(String assetName, Pageable pageable) {
        log.info("Finding media assets by asset name containing '{}' with pagination: {}", assetName, pageable);
        return mediaRepository.findByAssetNameContainingIgnoreCase(assetName, pageable)
                .flatMap(this::prepareDto)
                .doOnComplete(() -> log.info("Finished retrieving media assets by asset name containing '{}' for page {} with size {}.", assetName, pageable.getPageNumber(), pageable.getPageSize()))
                .doOnError(e -> log.error("Error retrieving media assets by asset name {}: {}", assetName, e.getMessage(), e));
    }

    /**
     * Counts media assets by their entity type.
     *
     * @param entityType The type of the entity.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countMediaAssetsByEntityType(String entityType) {
        log.info("Counting media assets by entity type: {}", entityType);
        return mediaRepository.countByEntityType(entityType)
                .doOnSuccess(count -> log.info("Total media asset count for entity type {}: {}", entityType, count))
                .doOnError(e -> log.error("Error counting media assets by entity type {}: {}", entityType, e.getMessage(), e));
    }

    /**
     * Counts media assets by their file type.
     *
     * @param fileType The type of the file.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countMediaAssetsByFileType(String fileType) {
        log.info("Counting media assets by file type: {}", fileType);
        return mediaRepository.countByFileType(fileType)
                .doOnSuccess(count -> log.info("Total media asset count for file type {}: {}", fileType, count))
                .doOnError(e -> log.error("Error counting media assets by file type {}: {}", fileType, e.getMessage(), e));
    }

    /**
     * Counts media assets whose asset name contains the specified string (case-insensitive).
     *
     * @param assetName The asset name string to search for.
     * @return A Mono emitting the count.
     */
    public Mono<Long> countMediaAssetsByAssetNameContaining(String assetName) {
        log.info("Counting media assets by asset name containing '{}'", assetName);
        return mediaRepository.countByAssetNameContainingIgnoreCase(assetName)
                .doOnSuccess(count -> log.info("Total media asset count for asset name '{}': {}", assetName, count))
                .doOnError(e -> log.error("Error counting media assets by asset name {}: {}", assetName, e.getMessage(), e));
    }

    /**
     * Checks if a media asset with a given unique file name exists.
     *
     * @param uniqueFileName The unique file name.
     * @return A Mono emitting true if it exists, false otherwise.
     */
    public Mono<Boolean> existsByUniqueFileName(String uniqueFileName) {
        log.info("Checking if media asset exists for unique file name: {}", uniqueFileName);
        return mediaRepository.existsByUniqueFileName(uniqueFileName)
                .doOnSuccess(exists -> log.info("Media asset for unique file name {} exists: {}", uniqueFileName, exists))
                .doOnError(e -> log.error("Error checking media asset existence for unique file name {}: {}", uniqueFileName, e.getMessage(), e));
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
