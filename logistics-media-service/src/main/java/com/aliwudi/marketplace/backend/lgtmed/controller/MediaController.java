package com.aliwudi.marketplace.backend.lgtmed.controller;

import com.aliwudi.marketplace.backend.common.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.service.MediaService;
import com.aliwudi.marketplace.backend.common.exception.MediaAssetNotFoundException;
import com.aliwudi.marketplace.backend.common.exception.InvalidMediaDataException;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Endpoint to upload a new media asset.
     *
     * @param request The MediaUploadRequest containing asset details and content.
     * @return A Mono emitting the created MediaAsset.
     * @throws IllegalArgumentException if input validation fails.
     * @throws InvalidMediaDataException if file type is unsupported, content is invalid, or duplicate unique file name.
     */
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 Created
    @PreAuthorize("hasRole('admin') or hasRole('seller') or hasRole('user')")
    public Mono<MediaAsset> uploadMedia(@Valid @RequestBody MediaUploadRequest request) {
        // Basic input validation
        if (request.getAssetName() == null || request.getAssetName().isBlank()
                || request.getFileContent() == null || request.getFileContent().isBlank()
                || request.getFileType() == null || request.getFileType().isBlank()
                || request.getEntityId() == null || request.getEntityId().isBlank()
                || request.getEntityType() == null || request.getEntityType().isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_MEDIA_UPLOAD_REQUEST);
        }

        return mediaService.uploadMedia(request);
        // Exceptions (InvalidMediaDataException) are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve a media asset by its unique file name.
     *
     * @param uniqueFileName The unique file name.
     * @return A Mono emitting the MediaAsset.
     * @throws IllegalArgumentException if unique file name is invalid.
     * @throws MediaAssetNotFoundException if the media asset is not found.
     */
    @GetMapping("/{uniqueFileName}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<MediaAsset> getMediaAsset(@PathVariable String uniqueFileName) {
        if (uniqueFileName == null || uniqueFileName.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_UNIQUE_FILE_NAME);
        }
        return mediaService.getMediaAssetByUniqueFileName(uniqueFileName);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to retrieve media assets for a specific entity with pagination.
     *
     * @param entityId The ID of the entity.
     * @param entityType The type of the entity.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaAsset records.
     * @throws IllegalArgumentException if entity identifiers or pagination parameters are invalid.
     */
    @GetMapping("/entity/{entityId}/{entityType}")
    @ResponseStatus(HttpStatus.OK)
    public Flux<MediaAsset> getMediaAssetsForEntity(
            @PathVariable String entityId,
            @PathVariable String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        if (entityId == null || entityId.isBlank() || entityType == null || entityType.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return mediaService.getMediaAssetsForEntity(entityId, entityType, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count media assets for a specific entity.
     *
     * @param entityId The ID of the entity.
     * @param entityType The type of the entity.
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if entity identifiers are invalid.
     */
    @GetMapping("/entity/{entityId}/{entityType}/count")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Long> countMediaAssetsForEntity(
            @PathVariable String entityId,
            @PathVariable String entityType) {
        if (entityId == null || entityId.isBlank() || entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS);
        }
        return mediaService.countMediaAssetsForEntity(entityId, entityType);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to delete a media asset by its unique file name.
     *
     * @param uniqueFileName The unique file name of the media asset to delete.
     * @return A Mono<Void> indicating completion (HTTP 204 No Content).
     * @throws IllegalArgumentException if unique file name is invalid.
     * @throws MediaAssetNotFoundException if the media asset is not found.
     */
    @DeleteMapping("/admin/{uniqueFileName}") // Updated path for admin access
    @ResponseStatus(HttpStatus.NO_CONTENT) // HTTP 204 No Content
    @PreAuthorize("hasRole('admin') or hasRole('seller')")
    public Mono<Void> deleteMediaAsset(@PathVariable String uniqueFileName) {
        if (uniqueFileName == null || uniqueFileName.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_UNIQUE_FILE_NAME);
        }
        return mediaService.deleteMediaAsset(uniqueFileName);
        // Exceptions are handled by GlobalExceptionHandler.
    }

    // --- NEW: Controller Endpoints for all MediaAssetRepository methods ---
    /**
     * Endpoint to retrieve all media assets with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaAsset records.
     * @throws IllegalArgumentException if pagination parameters are invalid.
     */
    @GetMapping("/admin/all") // Updated path for admin access
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<MediaAsset> getAllMediaAssetsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return mediaService.findAllMediaAssets(pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find media assets by their entity type with pagination.
     *
     * @param entityType The type of the entity.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaAsset records.
     * @throws IllegalArgumentException if entity type or pagination parameters are invalid.
     */
    @GetMapping("/admin/byEntityType/{entityType}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<MediaAsset> getMediaAssetsByEntityType(
            @PathVariable String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (entityType == null || entityType.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return mediaService.findMediaAssetsByEntityType(entityType, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find media assets by their file type with pagination.
     *
     * @param fileType The type of the file (e.g., "image/jpeg", "video/mp4").
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaAsset records.
     * @throws IllegalArgumentException if file type or pagination parameters are invalid.
     */
    @GetMapping("/admin/byFileType/{fileType}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<MediaAsset> getMediaAssetsByFileType(
            @PathVariable String fileType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (fileType == null || fileType.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_FILE_TYPE + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return mediaService.findMediaAssetsByFileType(fileType, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to find media assets whose asset name contains the specified
     * string (case-insensitive), with pagination.
     *
     * @param assetName The asset name string to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaAsset records.
     * @throws IllegalArgumentException if asset name or pagination parameters are invalid.
     */
    @GetMapping("/admin/byAssetNameContaining")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Flux<MediaAsset> getMediaAssetsByAssetNameContaining(
            @RequestParam String assetName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (assetName == null || assetName.isBlank() || page < 0 || size <= 0) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ASSET_NAME + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS);
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return mediaService.findMediaAssetsByAssetNameContaining(assetName, pageable);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count media assets by their entity type.
     *
     * @param entityType The type of the entity.
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if entity type is invalid.
     */
    @GetMapping("/count/byEntityType/{entityType}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countMediaAssetsByEntityType(@PathVariable String entityType) {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS);
        }
        return mediaService.countMediaAssetsByEntityType(entityType);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count media assets by their file type.
     *
     * @param fileType The type of the file.
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if file type is invalid.
     */
    @GetMapping("/count/byFileType/{fileType}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countMediaAssetsByFileType(@PathVariable String fileType) {
        if (fileType == null || fileType.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_FILE_TYPE);
        }
        return mediaService.countMediaAssetsByFileType(fileType);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to count media assets whose asset name contains the specified
     * string (case-insensitive).
     *
     * @param assetName The asset name string to search for.
     * @return A Mono emitting the count.
     * @throws IllegalArgumentException if asset name is invalid.
     */
    @GetMapping("/count/byAssetNameContaining")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('admin')")
    public Mono<Long> countMediaAssetsByAssetNameContaining(@RequestParam String assetName) {
        if (assetName == null || assetName.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_ASSET_NAME);
        }
        return mediaService.countMediaAssetsByAssetNameContaining(assetName);
        // Errors are handled by GlobalExceptionHandler.
    }

    /**
     * Endpoint to check if a media asset with a given unique file name exists.
     *
     * @param uniqueFileName The unique file name.
     * @return A Mono emitting true if it exists, false otherwise (Boolean).
     * @throws IllegalArgumentException if unique file name is invalid.
     */
    @GetMapping("/exists/{uniqueFileName}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Boolean> existsByUniqueFileName(@PathVariable String uniqueFileName) {
        if (uniqueFileName == null || uniqueFileName.isBlank()) {
            throw new IllegalArgumentException(ApiResponseMessages.INVALID_UNIQUE_FILE_NAME);
        }
        return mediaService.existsByUniqueFileName(uniqueFileName);
        // Errors are handled by GlobalExceptionHandler.
    }
}
