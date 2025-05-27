package com.aliwudi.marketplace.backend.lgtmed.controller;


import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.service.MediaService;
import com.ecommerce.logisticsmedia.media.dto.MediaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload")
    public ResponseEntity<MediaResponse> uploadMedia(@RequestBody MediaUploadRequest request) {
        // IMPORTANT: In a real application, you would use @RequestParam MultipartFile file
        // and handle the actual file upload logic to cloud storage (S3, GCS etc.) here.
        // The current implementation is a simplification using base64 for demonstration.
        MediaAsset asset = mediaService.uploadMedia(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(asset, "Media uploaded successfully"));
    }

    @GetMapping("/{uniqueFileName}")
    public ResponseEntity<MediaResponse> getMediaAsset(@PathVariable String uniqueFileName) {
        MediaAsset asset = mediaService.getMediaAssetByUniqueFileName(uniqueFileName);
        return ResponseEntity.ok(convertToDto(asset, "Media asset found"));
    }

    @GetMapping("/entity/{entityId}/{entityType}")
    public ResponseEntity<List<MediaResponse>> getMediaAssetsForEntity(
            @PathVariable String entityId,
            @PathVariable String entityType) {
        List<MediaAsset> assets = mediaService.getMediaAssetsForEntity(entityId, entityType);
        List<MediaResponse> responses = assets.stream()
                .map(asset -> convertToDto(asset, "Assets for entity found"))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{uniqueFileName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMediaAsset(@PathVariable String uniqueFileName) {
        mediaService.deleteMediaAsset(uniqueFileName);
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