package com.aliwudi.marketplace.backend.lgtmed.service;

import com.aliwudi.marketplace.backend.lgtmed.dto.MediaUploadRequest;
import com.aliwudi.marketplace.backend.lgtmed.exception.InvalidMediaDataException;
import com.aliwudi.marketplace.backend.lgtmed.exception.MediaAssetNotFoundException;
import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import com.aliwudi.marketplace.backend.lgtmed.repository.MediaRepository;
import com.aliwudi.marketplace.backend.common.response.ApiResponseMessages;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService{

    private final MediaRepository mediaRepository;

    // In a real application, you'd inject a cloud storage client (e.g., S3Client, GcsClient)
    // For demonstration, we'll simulate storage operations.
    // private final S3Client s3Client; // Example

    
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
                            .uploadDate(LocalDateTime.now())
                            // .fileSize(fileSize) // If calculated
                            .build();

                    return mediaRepository.save(mediaAsset);
                })
                .onErrorResume(e -> {
                    System.err.println("Error in uploadMedia service: " + e.getMessage());
                    if (e instanceof InvalidMediaDataException) {
                        return Mono.error(e); // Re-throw specific exception
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_UPLOADING_MEDIA + ": " + e.getMessage()));
                });
    }

    
    public Mono<MediaAsset> getMediaAssetByUniqueFileName(String uniqueFileName) {
        return mediaRepository.findByUniqueFileName(uniqueFileName)
                .switchIfEmpty(Mono.error(new MediaAssetNotFoundException(ApiResponseMessages.MEDIA_NOT_FOUND + uniqueFileName)))
                .onErrorResume(e -> {
                    System.err.println("Error in getMediaAssetByUniqueFileName service: " + e.getMessage());
                    if (e instanceof MediaAssetNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_MEDIA + ": " + e.getMessage()));
                });
    }

    
    public Flux<MediaAsset> getMediaAssetsForEntity(String entityId, String entityType, Long offset, Integer limit) {
        return mediaRepository.findByEntityIdAndEntityType(entityId, entityType)
                .skip(offset)
                .take(limit)
                .onErrorResume(e -> {
                    System.err.println("Error in getMediaAssetsForEntity service: " + e.getMessage());
                    return Flux.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_ENTITY_MEDIA + ": " + e.getMessage()));
                });
    }

    
    public Mono<Long> countMediaAssetsForEntity(String entityId, String entityType) {
        return mediaRepository.countByEntityIdAndEntityType(entityId, entityType)
                .onErrorResume(e -> {
                    System.err.println("Error in countMediaAssetsForEntity service: " + e.getMessage());
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_COUNTING_ENTITY_MEDIA + ": " + e.getMessage()));
                });
    }

    
    public Mono<Void> deleteMediaAsset(String uniqueFileName) {
        // In a real scenario, you would delete from cloud storage first, then from DB
        return mediaRepository.findByUniqueFileName(uniqueFileName)
                .switchIfEmpty(Mono.error(new MediaAssetNotFoundException(ApiResponseMessages.MEDIA_NOT_FOUND_FOR_DELETE + uniqueFileName)))
                .flatMap(asset -> {
                    // Simulate deletion from cloud storage:
                    // return Mono.fromCallable(() -> s3Client.deleteObject(DeleteObjectRequest.builder()...))
                    //            .then(mediaRepository.delete(asset));
                    System.out.println("Simulating deletion of " + asset.getUrl() + " from cloud storage.");
                    return mediaRepository.delete(asset);
                })
                .then() // Ensure Mono<Void> is returned
                .onErrorResume(e -> {
                    System.err.println("Error in deleteMediaAsset service: " + e.getMessage());
                    if (e instanceof MediaAssetNotFoundException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_DELETING_MEDIA + ": " + e.getMessage()));
                });
    }

    // NEW: Implement getAllMediaAssets
    
    public Flux<MediaAsset> getAllMediaAssets(Long offset, Integer limit) {
        return mediaRepository.findAll()
                .skip(offset)
                .take(limit)
                .onErrorResume(e -> {
                    System.err.println("Error in getAllMediaAssets service: " + e.getMessage());
                    return Flux.error(new RuntimeException(ApiResponseMessages.ERROR_RETRIEVING_ALL_MEDIA + ": " + e.getMessage()));
                });
    }

    // NEW: Implement countAllMediaAssets
    
    public Mono<Long> countAllMediaAssets() {
        return mediaRepository.count()
                .onErrorResume(e -> {
                    System.err.println("Error in countAllMediaAssets service: " + e.getMessage());
                    return Mono.error(new RuntimeException(ApiResponseMessages.ERROR_COUNTING_ALL_MEDIA + ": " + e.getMessage()));
                });
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