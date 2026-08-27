package org.hlopes.library.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hlopes.auth.entity.User;
import org.hlopes.auth.repository.UserRepository;
import org.hlopes.catalog.dto.MediaItemDto;
import org.hlopes.catalog.entity.MediaItem;
import org.hlopes.catalog.entity.MediaTypeEnum;
import org.hlopes.catalog.repository.MediaItemRepository;
import org.hlopes.library.dto.LibraryEntryResponse;
import org.hlopes.library.entity.LibraryEntry;
import org.hlopes.library.entity.StatusEnum;
import org.hlopes.library.repository.LibraryEntryRepository;
import org.hlopes.library.repository.SeasonWatchRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class LibraryService {

    @Inject
    UserRepository userRepository;

    @Inject
    MediaItemRepository mediaItemRepository;

    @Inject
    LibraryEntryRepository libraryEntryRepository;

    @Inject
    SeasonWatchRepository seasonWatchRepository;

    @Transactional
    public LibraryEntryResponse add(String email, Long externalId, String rawMediaType) {
        return add(email, externalId, rawMediaType, null, null);
    }

    @Transactional
    public LibraryEntryResponse add(
            String email, Long externalId, String rawMediaType, String rawStatus, Integer rating) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));

        String normalized = rawMediaType == null ? "" : rawMediaType.trim().toLowerCase();
        MediaTypeEnum mediaType;

        if (normalized.equals("movie")) {
            mediaType = MediaTypeEnum.MOVIE;
        } else if (normalized.equals("tv") || normalized.equals("tv_series") || normalized.equals("tv-series")) {
            mediaType = MediaTypeEnum.TV_SERIES;
        } else {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "mediaType must be movie or tv"))
                    .build());
        }

        StatusEnum status = StatusEnum.WISHLIST;

        if (rawStatus != null && !rawStatus.isBlank()) {
            try {
                status = StatusEnum.valueOf(rawStatus.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid status"))
                        .build());
            }
        }

        validateRatingForStatus(status, mediaType, rating);

        MediaItem mediaItem = mediaItemRepository
                .findByExternalIdAndMediaType(externalId, mediaType)
                .orElse(null);

        if (mediaItem == null) {
            try {
                mediaItem = mediaItemRepository
                        .findByExternalIdAndMediaType(externalId, mediaType)
                        .orElseThrow(() -> new WebApplicationException(Response.status(404)
                                .entity(Map.of("error", "media not found"))
                                .build()));
            } catch (WebApplicationException e) {
                throw e;
            } catch (Exception e) {
                throw new WebApplicationException(Response.status(404)
                        .entity(Map.of("error", "media not found"))
                        .build());
            }
        }

        if (libraryEntryRepository.existsByUserIdAndMediaItemId(user.id, mediaItem.id)) {
            String msg = status == StatusEnum.COMPLETED ? "already in library" : "already in wishlist";
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", msg))
                    .build());
        }

        LibraryEntry entry = new LibraryEntry();
        entry.user = user;
        entry.mediaItem = mediaItem;
        entry.status = status;
        entry.rating = rating;
        libraryEntryRepository.persist(entry);

        MediaItemDto mediaItemDto = new MediaItemDto(
                mediaItem.id,
                mediaItem.externalId,
                mediaItem.mediaType.name(),
                mediaItem.title,
                mediaItem.synopsis,
                mediaItem.posterPath,
                mediaItem.backdropPath,
                mediaItem.releaseDate);

        return new LibraryEntryResponse(entry.id, entry.status.name(), mediaItemDto, entry.createdAt, entry.rating);
    }

    public List<LibraryEntryResponse> list(String email, String statusParam, int page, int size) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));

        StatusEnum status = StatusEnum.WISHLIST;

        if (statusParam != null && !statusParam.isBlank()) {
            try {
                status = StatusEnum.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid status"))
                        .build());
            }
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        List<LibraryEntry> entries = libraryEntryRepository.findByUserIdAndStatus(user.id, status, safePage, safeSize);

        return entries.stream()
                .map(e -> new LibraryEntryResponse(
                        e.id,
                        e.status.name(),
                        new MediaItemDto(
                                e.mediaItem.id,
                                e.mediaItem.externalId,
                                e.mediaItem.mediaType.name(),
                                e.mediaItem.title,
                                e.mediaItem.synopsis,
                                e.mediaItem.posterPath,
                                e.mediaItem.backdropPath,
                                e.mediaItem.releaseDate),
                        e.createdAt,
                        e.rating))
                .toList();
    }

    public long count(String email, String statusParam) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));

        StatusEnum status = StatusEnum.WISHLIST;

        if (statusParam != null && !statusParam.isBlank()) {
            try {
                status = StatusEnum.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid status"))
                        .build());
            }
        }

        return libraryEntryRepository.countByUserIdAndStatus(user.id, status);
    }

    public List<LibraryEntryResponse> listWatched(String email, int page, int size) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);

        List<LibraryEntry> entries = libraryEntryRepository.findWatched(user.id, safePage, safeSize);

        return entries.stream()
                .map(e -> new LibraryEntryResponse(
                        e.id,
                        e.status.name(),
                        new MediaItemDto(
                                e.mediaItem.id,
                                e.mediaItem.externalId,
                                e.mediaItem.mediaType.name(),
                                e.mediaItem.title,
                                e.mediaItem.synopsis,
                                e.mediaItem.posterPath,
                                e.mediaItem.backdropPath,
                                e.mediaItem.releaseDate),
                        e.createdAt,
                        e.rating))
                .toList();
    }

    public long countWatched(String email) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));

        return libraryEntryRepository.countWatched(user.id);
    }

    @Transactional
    public LibraryEntryResponse update(String email, UUID entryId, String rawStatus, Integer rating) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));

        LibraryEntry entry = libraryEntryRepository
                .findByIdAndUserId(entryId, user.id)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "entry not found"))
                        .build()));

        StatusEnum targetStatus = entry.status;
        boolean statusProvided = rawStatus != null && !rawStatus.isBlank();

        if (statusProvided) {
            try {
                targetStatus = StatusEnum.valueOf(rawStatus.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid status"))
                        .build());
            }
        }

        Integer targetRating = rating;
        boolean ratingProvided = rating != null;

        if (!ratingProvided && !statusProvided) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "status or rating must be provided"))
                    .build());
        }

        if (!ratingProvided) {
            if (targetStatus == StatusEnum.COMPLETED
                    && entry.rating == null
                    && entry.mediaItem.mediaType != MediaTypeEnum.TV_SERIES) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "rating required for COMPLETED"))
                        .build());
            }
            // also check if TV_SERIES with no granular watches still requires rating when targeting COMPLETED without
            // providing one
            if (!ratingProvided
                    && targetStatus == StatusEnum.COMPLETED
                    && entry.mediaItem.mediaType == MediaTypeEnum.TV_SERIES
                    && entry.rating == null) {
                long existingWatches = seasonWatchRepository.countByUserIdAndMediaItemId(user.id, entry.mediaItem.id);

                if (existingWatches == 0) {
                    throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "rating required for COMPLETED"))
                            .build());
                }
            }
            if (targetStatus == StatusEnum.COMPLETED) {
                targetRating = entry.rating;
            } else if (targetStatus == StatusEnum.WISHLIST) {
                targetRating = null;
            } else {
                targetRating = entry.rating;
            }
        }

        validateRatingForStatus(targetStatus, entry.mediaItem.mediaType, targetRating);

        entry.status = targetStatus;
        entry.rating = targetRating;
        libraryEntryRepository.persist(entry);

        MediaItem mediaItem = entry.mediaItem;
        MediaItemDto mediaItemDto = new MediaItemDto(
                mediaItem.id,
                mediaItem.externalId,
                mediaItem.mediaType.name(),
                mediaItem.title,
                mediaItem.synopsis,
                mediaItem.posterPath,
                mediaItem.backdropPath,
                mediaItem.releaseDate);

        return new LibraryEntryResponse(entry.id, entry.status.name(), mediaItemDto, entry.createdAt, entry.rating);
    }

    @Transactional
    public void remove(String email, UUID entryId) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));

        LibraryEntry entry = libraryEntryRepository
                .findByIdAndUserId(entryId, user.id)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "entry not found"))
                        .build()));

        // cascade delete season watches for TV series

        try {
            if (entry.mediaItem != null && entry.mediaItem.mediaType == MediaTypeEnum.TV_SERIES) {
                seasonWatchRepository.deleteByUserIdAndMediaItemId(user.id, entry.mediaItem.id);
            }
        } catch (Exception ignored) {
        }
        libraryEntryRepository.delete(entry);
    }

    private void validateRatingForStatus(StatusEnum status, MediaTypeEnum mediaType, Integer rating) {
        if (status == StatusEnum.COMPLETED) {
            if (mediaType == MediaTypeEnum.TV_SERIES) {
                // TV Series: rating optional when granular watches exist, but if provided must be 1-5
                // Also allow null for granular case; enforce required only when no watches (handled in call site)
                // For direct add without context of watches, allow null to support granular path; service caller
                // decides
                // We treat null as allowed here; the stricter check for non-granular is done in update()

                if (rating != null && (rating < 1 || rating > 5)) {
                    throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "rating must be between 1 and 5"))
                            .build());
                }
                // For TV series, do not enforce rating required generically — allow null for granular flow
                // But for Movie-like behavior when adding TV without watches, the caller (LibraryService.add) will
                // still
                // need to decide: we allow null to support episode-driven completion; frontend shortcut will still
                // provide rating
                // To preserve validation for one-shot TV without episodes, we keep soft check: if this is called from
                // add()
                // with status COMPLETED and rating null, we consider if we should require it — we allow null to unblock
                // granular

                return;
            }
            if (rating == null) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "rating required for COMPLETED"))
                        .build());
            }
            if (rating < 1 || rating > 5) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "rating must be between 1 and 5"))
                        .build());
            }
        } else if (status == StatusEnum.WISHLIST) {
            if (rating != null) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "rating not allowed for WISHLIST"))
                        .build());
            }
        } else {
            if (rating != null && (rating < 1 || rating > 5)) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "rating must be between 1 and 5"))
                        .build());
            }
        }
    }
}
