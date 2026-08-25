package org.hlopes.repository;

import java.util.Optional;

import org.hlopes.entity.MediaItem;
import org.hlopes.entity.MediaType;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MediaItemRepository implements PanacheRepositoryBase<MediaItem, java.util.UUID> {

    public Optional<MediaItem> findByExternalIdAndMediaType(Long externalId, MediaType mediaType) {
        return find("externalId = ?1 and mediaType = ?2", externalId, mediaType).firstResultOptional();
    }
}
