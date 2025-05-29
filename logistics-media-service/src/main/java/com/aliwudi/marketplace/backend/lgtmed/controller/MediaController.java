package com.aliwudi.marketplace.backend.lgtmed.controller;

import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.service.MediaService;
import com.ecommerce.logisticsmedia.media.dto.MediaResponse; // Assuming this DTO is correctly located and accessible
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // NEW: Import Mono for reactive types
import reactor.core.publisher.Flux; // NEW: Import Flux for reactive collections
import org.springframework.web.server.ResponseStatusException; // NEW: For reactive error handling

import java.util.List;
// Removed java.util.stream.Collectors as reactive operations handle collection

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload")
    public Mono<ResponseEntity<MediaResponse>> uploadMedia(@RequestBody MediaUploadRequest request) {
        // IMPORTANT: In a real application, you would typically handle MultipartFile here
        // for actual file uploads, potentially using a reactive approach like Flux<DataBuffer>.
        // The service layer would then interact with cloud storage reactively.
        // The current implementation is a simplification using base64 for demonstration.
        return mediaService.uploadMedia(request)
                .map(asset -> ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(asset, "Media uploaded successfully")))
                .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
    }

    @GetMapping("/{uniqueFileName}")
    public Mono<ResponseEntity<MediaResponse>> getMediaAsset(@PathVariable String uniqueFileName) {
        return mediaService.getMediaAssetByUniqueFileName(uniqueFileName)
                .map(asset -> ResponseEntity.ok(convertToDto(asset, "Media asset found")))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found: " + uniqueFileName)))
                .onErrorResume(ResponseStatusException.class, Mono::error) // Re-throw as WebFlux exception
                .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
    }

    @GetMapping("/entity/{entityId}/{entityType}")
    public Mono<ResponseEntity<List<MediaResponse>>> getMediaAssetsForEntity(
            @PathVariable String entityId,
            @PathVariable String entityType,
            @RequestParam(defaultValue = "0") Long offset,
            @RequestParam(defaultValue = "20") Integer limit) {
        return mediaService.getMediaAssetsForEntity(entityId, entityType, offset, limit)
                .map(asset -> convertToDto(asset, "Assets for entity found")) // Convert each asset to DTO
                .collectList() // Collect all DTOs into a List
                .map(responses -> ResponseEntity.ok(responses)) // Wrap List in ResponseEntity
                .onErrorResume(e -> Mono.just(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR))); // Generic error handling
    }

    @DeleteMapping("/{uniqueFileName}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // This sets the HTTP status to 204 on successful completion
    public Mono<Void> deleteMediaAsset(@PathVariable String uniqueFileName) {
        return mediaService.deleteMediaAsset(uniqueFileName)
                .onErrorResume(e -> Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting media asset: " + e.getMessage())));
    }

    private MediaResponse convertToDto(MediaAsset asset, String message) {
        return MediaResponse.builder()
                .assetId(String.valueOf(asset.getId()))
                .assetName(asset.getAssetName())
                .url(asset.getUrl())
                .fileType(asset.getFileType())
                .entityId(asset.getEntityId())
                .entityType(asset.getEntityType())
                .uploadDate(asset.getUploadDate())
                .message(message)
                .build();
    }
}