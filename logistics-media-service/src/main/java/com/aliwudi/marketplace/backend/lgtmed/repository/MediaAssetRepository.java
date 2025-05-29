package com.aliwudi.marketplace.backend.lgtmed.repository;

import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MediaAssetRepository extends ReactiveCrudRepository<MediaAsset, Long> {
    Mono<MediaAsset> findByUniqueFileName(String uniqueFileName);
    Flux<MediaAsset> findByEntityIdAndEntityType(String entityId, String entityType, Long offset, Integer limit);
}