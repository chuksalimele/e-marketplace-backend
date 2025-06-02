package com.aliwudi.marketplace.backend.lgtmed.controller;

import com.aliwudi.marketplace.backend.common.dto.MediaDto;
import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.service.MediaService;
import com.aliwudi.marketplace.backend.lgtmed.exception.MediaAssetNotFoundException;
import com.aliwudi.marketplace.backend.lgtmed.exception.InvalidMediaDataException;
import com.aliwudi.marketplace.backend.common.response.StandardResponseEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.data.domain.Pageable; // For pagination
import org.springframework.data.domain.PageRequest; // For creating Pageable instances
import org.springframework.data.domain.Sort; // For sorting

import java.util.List;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Helper method to map MediaAsset entity to MediaDto DTO for public exposure.
     */
    private MediaDto mapMediaAssetToMediaDto(MediaAsset asset) {
        if (asset == null) {
            return null;
        }
        return MediaDto.builder()
                .id(asset.getId())
                .assetName(asset.getAssetName())
                .uniqueFileName(asset.getUniqueFileName())
                .url(asset.getUrl())
                .fileType(asset.getFileType())
                .entityId(asset.getEntityId())
                .entityType(asset.getEntityType())
                .uploadTime(asset.getUploadTime())
                .build();
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER') or hasRole('USER')")
    public Mono<StandardResponseEntity> uploadMedia(@Valid @RequestBody MediaUploadRequest request) {
        if (request.getAssetName() == null || request.getAssetName().isBlank() ||
            request.getFileContent() == null || request.getFileContent().isBlank() ||
            request.getFileType() == null || request.getFileType().isBlank() ||
            request.getEntityId() == null || request.getEntityId().isBlank() ||
            request.getEntityType() == null || request.getEntityType().isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_MEDIA_UPLOAD_REQUEST));
        }

        return mediaService.uploadMedia(request)
            .map(asset -> (StandardResponseEntity) StandardResponseEntity.created(mapMediaAssetToMediaDto(asset), ApiResponseMessages.MEDIA_UPLOAD_SUCCESS))
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
            .map(asset -> (StandardResponseEntity) StandardResponseEntity.ok(mapMediaAssetToMediaDto(asset), ApiResponseMessages.MEDIA_RETRIEVED_SUCCESS))
            .onErrorResume(MediaAssetNotFoundException.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.notFound(e.getMessage())))
            .onErrorResume(Exception.class, e ->
                    Mono.just((StandardResponseEntity) StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_RETRIEVING_MEDIA + ": " + e.getMessage())));
    }

    @GetMapping("/entity/{entityId}/{entityType}")
    public Mono<StandardResponseEntity> getMediaAssetsForEntity(
            @PathVariable String entityId,
            @PathVariable String entityType,
            @RequestParam(defaultValue = "0") int page, // Changed to int for PageRequest
            @RequestParam(defaultValue = "20") int size, // Changed to int for PageRequest
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        if (entityId == null || entityId.isBlank() || entityType == null || entityType.isBlank()) {
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS));
        }
        if (page < 0 || size <= 0) { // Check page and size
            return Mono.just((StandardResponseEntity) StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort); // Create Pageable

        return mediaService.getMediaAssetsForEntity(entityId, entityType, pageable) // Pass Pageable
            .map(this::mapMediaAssetToMediaDto)
            .collectList()
            .map(responses -> (StandardResponseEntity) StandardResponseEntity.ok(responses, ApiResponseMessages.MEDIA_ASSETS_RETRIEVED_SUCCESS))
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
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

    // --- NEW: Controller Endpoints for all MediaAssetRepository methods ---

    /**
     * Endpoint to retrieve all media assets with pagination.
     *
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaDto records.
     */
    @GetMapping("/admin/all-paginated") // Renamed to avoid conflict with existing /
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<MediaDto> getAllMediaAssetsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (page < 0 || size <= 0) {
            return Flux.error(new InvalidMediaDataException(ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return mediaService.findAllMediaAssets(pageable)
                .map(this::mapMediaAssetToMediaDto);
    }

    /**
     * Endpoint to find media assets by their entity type with pagination.
     *
     * @param entityType The type of the entity.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaDto records.
     */
    @GetMapping("/admin/byEntityType/{entityType}")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<MediaDto> getMediaAssetsByEntityType(
            @PathVariable String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (entityType == null || entityType.isBlank() || page < 0 || size <= 0) {
            return Flux.error(new InvalidMediaDataException(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return mediaService.findMediaAssetsByEntityType(entityType, pageable)
                .map(this::mapMediaAssetToMediaDto);
    }

    /**
     * Endpoint to find media assets by their file type with pagination.
     *
     * @param fileType The type of the file (e.g., "image/jpeg", "video/mp4").
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaDto records.
     */
    @GetMapping("/admin/byFileType/{fileType}")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<MediaDto> getMediaAssetsByFileType(
            @PathVariable String fileType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (fileType == null || fileType.isBlank() || page < 0 || size <= 0) {
            return Flux.error(new InvalidMediaDataException(ApiResponseMessages.INVALID_FILE_TYPE + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return mediaService.findMediaAssetsByFileType(fileType, pageable)
                .map(this::mapMediaAssetToMediaDto);
    }

    /**
     * Endpoint to find media assets whose asset name contains the specified string (case-insensitive), with pagination.
     *
     * @param assetName The asset name string to search for.
     * @param page The page number (0-indexed).
     * @param size The number of items per page.
     * @param sortBy The field to sort by.
     * @param sortDir The sort direction (asc/desc).
     * @return A Flux of MediaDto records.
     */
    @GetMapping("/admin/byAssetNameContaining")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<MediaDto> getMediaAssetsByAssetNameContaining(
            @RequestParam String assetName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (assetName == null || assetName.isBlank() || page < 0 || size <= 0) {
            return Flux.error(new InvalidMediaDataException(ApiResponseMessages.INVALID_ASSET_NAME + " or " + ApiResponseMessages.INVALID_PAGINATION_PARAMETERS));
        }
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return mediaService.findMediaAssetsByAssetNameContaining(assetName, pageable)
                .map(this::mapMediaAssetToMediaDto);
    }

    /**
     * Endpoint to count media assets by their entity type.
     *
     * @param entityType The type of the entity.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byEntityType/{entityType}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countMediaAssetsByEntityType(@PathVariable String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ENTITY_IDENTIFIERS));
        }
        return mediaService.countMediaAssetsByEntityType(entityType)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.MEDIA_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_ENTITY_MEDIA + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count media assets by their file type.
     *
     * @param fileType The type of the file.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byFileType/{fileType}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countMediaAssetsByFileType(@PathVariable String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_FILE_TYPE));
        }
        return mediaService.countMediaAssetsByFileType(fileType)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.MEDIA_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_MEDIA_BY_FILE_TYPE + ": " + e.getMessage())));
    }

    /**
     * Endpoint to count media assets whose asset name contains the specified string (case-insensitive).
     *
     * @param assetName The asset name string to search for.
     * @return A Mono emitting StandardResponseEntity with the count.
     */
    @GetMapping("/count/byAssetNameContaining")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandardResponseEntity> countMediaAssetsByAssetNameContaining(@RequestParam String assetName) {
        if (assetName == null || assetName.isBlank()) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_ASSET_NAME));
        }
        return mediaService.countMediaAssetsByAssetNameContaining(assetName)
                .map(count -> StandardResponseEntity.ok(count, ApiResponseMessages.MEDIA_COUNT_RETRIEVED_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_COUNTING_MEDIA_BY_ASSET_NAME + ": " + e.getMessage())));
    }

    /**
     * Endpoint to check if a media asset with a given unique file name exists.
     *
     * @param uniqueFileName The unique file name.
     * @return A Mono emitting StandardResponseEntity with a boolean indicating existence.
     */
    @GetMapping("/exists/{uniqueFileName}")
    public Mono<StandardResponseEntity> existsByUniqueFileName(@PathVariable String uniqueFileName) {
        if (uniqueFileName == null || uniqueFileName.isBlank()) {
            return Mono.just(StandardResponseEntity.badRequest(ApiResponseMessages.INVALID_UNIQUE_FILE_NAME));
        }
        return mediaService.existsByUniqueFileName(uniqueFileName)
                .map(exists -> StandardResponseEntity.ok(exists, ApiResponseMessages.MEDIA_EXISTS_CHECK_SUCCESS))
                .onErrorResume(Exception.class, e ->
                        Mono.just(StandardResponseEntity.internalServerError(ApiResponseMessages.ERROR_CHECKING_MEDIA_EXISTENCE + ": " + e.getMessage())));
    }
}