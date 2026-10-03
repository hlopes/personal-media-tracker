package org.hlopes.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import org.hlopes.catalog.entity.MediaItem;
import org.hlopes.catalog.entity.MediaTypeEnum;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MediaItemRepository implements PanacheRepositoryBase<MediaItem, UUID> {

    public Optional<MediaItem> findByExternalIdAndMediaType(Long externalId, MediaTypeEnum mediaType) {
        return find("externalId = ?1 and mediaType = ?2", externalId, mediaType).firstResultOptional();
    }
}
