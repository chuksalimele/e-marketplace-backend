package com.aliwudi.marketplace.backend.lgtmed.repository;

import com.aliwudi.marketplace.backend.lgtmed.model.MediaAsset;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MediaRepository extends ReactiveCrudRepository<MediaAsset, Long> {
    Mono<MediaAsset> findByUniqueFileName(String uniqueFileName);
    Flux<MediaAsset> findByEntityIdAndEntityType(String entityId, String entityType);
    Mono<Long> countByEntityIdAndEntityType(String entityId, String entityType);
    Mono<Void> deleteByUniqueFileName(String uniqueFileName);
}