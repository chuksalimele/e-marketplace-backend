package com.aliwudi.marketplace.backend.lgtmed.repository;

import com.aliwudi.marketplace.backend.common.model.MediaAsset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface MediaRepository extends R2dbcRepository<MediaAsset, Long> {

    // --- Basic Retrieval & Pagination ---
    Flux<MediaAsset> findAllBy(Pageable pageable);

    // --- MediaAsset Specific Queries ---
    Flux<MediaAsset> findByEntityIdAndEntityType(String entityId, String entityType, Pageable pageable);
    Flux<MediaAsset> findByEntityType(String entityType, Pageable pageable);
    Flux<MediaAsset> findByFileType(String fileType, Pageable pageable);
    Flux<MediaAsset> findByAssetNameContainingIgnoreCase(String assetName, Pageable pageable);
    Mono<MediaAsset> findByUniqueFileName(String uniqueFileName); // Unique file name is unique

    // --- Count Queries ---
    Mono<Long> count();
    Mono<Long> countByEntityIdAndEntityType(String entityId, String entityType);
    Mono<Long> countByEntityType(String entityType);
    Mono<Long> countByFileType(String fileType);
    Mono<Long> countByAssetNameContainingIgnoreCase(String assetName);
    Mono<Boolean> existsByUniqueFileName(String uniqueFileName);
}