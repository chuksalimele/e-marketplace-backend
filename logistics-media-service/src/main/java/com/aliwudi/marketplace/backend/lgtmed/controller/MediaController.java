package com.aliwudi.marketplace.backend.lgtmed.controller;

import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.dto.MediaResponse; // Assuming this DTO is correctly located
import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.service.MediaService;
import com.aliwudi.marketplace.backend.lgtmed.exception.MediaAssetNotFoundException; // New custom exception
import com.aliwudi.marketplace.backend.lgtmed.exception.InvalidMediaDataException; // New custom exception
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize; // Assuming security is applied here
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Helper method to map MediaAsset entity to MediaResponse DTO for public exposure.
     */
    private MediaResponse mapMediaAssetToMediaResponse(MediaAsset asset) {
        if (asset == null) {
            return null;
        }
        return MediaResponse.builder()
                .assetId(String.valueOf(asset.getId())) // Convert Long ID to String if DTO expects String
                .assetName(asset.getAssetName())
                .uniqueFileName(asset.getUniqueFileName()) // Include uniqueFileName
                .url(asset.getUrl())
                .fileType(asset.getFileType())
                .entityId(asset.getEntityId())
                .entityType(asset.getEntityType())
                .uploadDate(asset.getUploadDate())
                .build();
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER') or hasRole('USER')") // Adjust roles as needed
    public Mono<StandardResponseEntity> uploadMedia(@Valid @RequestBody MediaUploadRequest request) {
        // Basic input validation for MediaUploadRequest
        if (request.getAssetName() == null || request.getAssetName().isBlank() ||
            request.getFileContent() == null || request.getFileContent().isBlank() || // Assuming base64 content
            request.getFileType() == null || request.getFileType().isBlank() ||
            request.getEntityId() == null || request.getEntityId().isBlank() ||
            request.getEntityType() == null || request.getEntityType().isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_MEDIA_UPLOAD_REQUEST));
        }

        return mediaService.uploadMedia(request)
            .map(asset -> (StandardResponseEntity) StandardResponseEntity.created(
                    mapMediaAssetToMediaResponse(asset), ApiResponseMessages.MEDIA_UPLOAD_SUCCESS))
            .onErrorResume(InvalidMediaDataException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_UPLOADING_MEDIA + ": " + e.getMessage())));
    }

    @GetMapping("/{uniqueFileName}")
    public Mono<StandardResponseEntity> getMediaAsset(@PathVariable String uniqueFileName) {
        if (uniqueFileName == null || uniqueFileName.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_UNIQUE_FILE_NAME));
        }

        return mediaService.getMediaAssetByUniqueFileName(uniqueFileName)
            .map(asset -> (StandardResponseEntity) StandardResponseEntity.ok(
                    mapMediaAssetToMediaResponse(asset), ApiResponseMessages.MEDIA_RETRIEVED_SUCCESS))
            .switchIfEmpty(Mono.error(new MediaAssetNotFoundException(ApiResponseMessages.MEDIA_NOT_FOUND + uniqueFileName)))
            .onErrorResume(MediaAssetNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_MEDIA + ": " + e.getMessage())));
    }

    @GetMapping("/entity/{entityId}/{entityType}")
    public Mono<StandardResponseEntity> getMediaAssetsForEntity(
            @PathVariable String entityId,
            @PathVariable String entityType,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (entityId == null || entityId.isBlank() || entityType == null || entityType.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS));
        }
        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return mediaService.getMediaAssetsForEntity(entityId, entityType, offset, limit)
            .map(this::mapMediaAssetToMediaResponse)
            .collectList()
            .map(responses -> (StandardResponseEntity) StandardResponseEntity.ok(
                    responses, ApiResponseMessages.MEDIA_ASSETS_RETRIEVED_SUCCESS))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ENTITY_MEDIA + ": " + e.getMessage())));
    }

    @GetMapping("/entity/{entityId}/{entityType}/count")
    public Mono<StandardResponseEntity> countMediaAssetsForEntity(
            @PathVariable String entityId,
            @PathVariable String entityType) {

        if (entityId == null || entityId.isBlank() || entityType == null || entityType.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS));
        }

        return mediaService.countMediaAssetsForEntity(entityId, entityType)
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.MEDIA_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ENTITY_MEDIA + ": " + e.getMessage())));
    }


    @DeleteMapping("/{uniqueFileName}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')") // Only authorized users can delete
    public Mono<StandardResponseEntity> deleteMediaAsset(@PathVariable String uniqueFileName) {
        if (uniqueFileName == null || uniqueFileName.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_UNIQUE_FILE_NAME));
        }

        return mediaService.deleteMediaAsset(uniqueFileName)
            .then(Mono.just((StandardResponseEntity) StandardResponseEntity.ok(null, ApiResponseMessages.MEDIA_DELETED_SUCCESS)))
            .onErrorResume(MediaAssetNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_DELETING_MEDIA + ": " + e.getMessage())));
    }

    // NEW: Get all media assets (admin only)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> getAllMediaAssets(
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (offset < 0 || limit <= 0) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        return mediaService.getAllMediaAssets(offset, limit)
                .map(this::mapMediaAssetToMediaResponse)
                .collectList()
                .map(responses -> (StandardResponseEntity) StandardResponseEntity.ok(
                        responses, ApiResponseMessages.MEDIA_ASSETS_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_ALL_MEDIA + ": " + e.getMessage())));
    }

    // NEW: Count all media assets (admin only)
    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countAllMediaAssets() {
        return mediaService.countAllMediaAssets()
                .map(count -> (StandardResponseEntity) StandardResponseEntity.ok(count, ApiResponseMessages.MEDIA_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ALL_MEDIA + ": " + e.getMessage())));
    }
}