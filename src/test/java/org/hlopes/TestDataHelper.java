package org.hlopes;

import org.hlopes.entity.MediaItem;
import org.hlopes.entity.MediaType;
import org.hlopes.repository.MediaItemRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestDataHelper {

    @Inject
    MediaItemRepository mediaItemRepository;

    @Transactional
    public MediaItem createMediaItem(Long externalId, MediaType mediaType, String title) {
        MediaItem item = new MediaItem();
        item.externalId = externalId;
        item.mediaType = mediaType;
        item.title = title;
        item.synopsis = "Synopsis for " + title;
        item.posterPath = "/poster.jpg";
        item.backdropPath = "/backdrop.jpg";
        item.releaseDate = java.time.LocalDate.of(2020, 1, 1);
        mediaItemRepository.persist(item);
        return item;
    }

    @Transactional
    public MediaItem createMediaItem(Long externalId, MediaType mediaType) {
        return createMediaItem(externalId, mediaType, "Test " + mediaType + " " + externalId);
    }
}
