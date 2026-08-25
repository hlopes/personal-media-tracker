package org.hlopes.catalog;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.hlopes.catalog.dto.TmdbMovieDetails;
import org.hlopes.catalog.dto.TmdbTvDetails;
import org.hlopes.entity.MediaItem;
import org.hlopes.entity.MediaType;
import org.hlopes.repository.MediaItemRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class MediaItemService {

    @Inject
    MediaItemRepository mediaItemRepository;

    @Transactional
    public MediaItem findOrCreateFromMovie(TmdbMovieDetails details) {
        return mediaItemRepository
                .findByExternalIdAndMediaType(details.id(), MediaType.MOVIE)
                .orElseGet(() -> {
                    MediaItem item = new MediaItem();
                    item.externalId = details.id();
                    item.mediaType = MediaType.MOVIE;
                    item.title = details.title();
                    item.synopsis = details.overview();
                    item.posterPath = details.posterPath();
                    item.backdropPath = details.backdropPath();
                    item.releaseDate = parseDate(details.releaseDate());
                    mediaItemRepository.persist(item);

                    return item;
                });
    }

    @Transactional
    public MediaItem findOrCreateFromTv(TmdbTvDetails details) {
        return mediaItemRepository
                .findByExternalIdAndMediaType(details.id(), MediaType.TV_SERIES)
                .orElseGet(() -> {
                    MediaItem item = new MediaItem();
                    item.externalId = details.id();
                    item.mediaType = MediaType.TV_SERIES;
                    item.title = details.name();
                    item.synopsis = details.overview();
                    item.posterPath = details.posterPath();
                    item.backdropPath = details.backdropPath();
                    item.releaseDate = parseDate(details.firstAirDate());
                    mediaItemRepository.persist(item);

                    return item;
                });
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
