package org.hlopes.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hlopes.catalog.CatalogService;
import org.hlopes.catalog.dto.MediaItemDto;
import org.hlopes.dto.LibraryEntryResponse;
import org.hlopes.entity.LibraryEntry;
import org.hlopes.entity.MediaItem;
import org.hlopes.entity.MediaType;
import org.hlopes.entity.Status;
import org.hlopes.entity.User;
import org.hlopes.repository.LibraryEntryRepository;
import org.hlopes.repository.MediaItemRepository;
import org.hlopes.repository.UserRepository;

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
    CatalogService catalogService;

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
        MediaType mediaType;

        if (normalized.equals("movie")) {
            mediaType = MediaType.MOVIE;
        } else if (normalized.equals("tv") || normalized.equals("tv_series") || normalized.equals("tv-series")) {
            mediaType = MediaType.TV_SERIES;
        } else {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "mediaType must be movie or tv"))
                    .build());
        }

        Status status = Status.WISHLIST;

        if (rawStatus != null && !rawStatus.isBlank()) {
            try {
                status = Status.valueOf(rawStatus.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid status"))
                        .build());
            }
        }

        validateRatingForStatus(status, rating);

        MediaItem mediaItem = mediaItemRepository
                .findByExternalIdAndMediaType(externalId, mediaType)
                .orElse(null);

        if (mediaItem == null) {
            try {
                var detail = catalogService.detail(normalized, externalId);
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
            String msg = status == Status.COMPLETED ? "already in library" : "already in wishlist";
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

        Status status = Status.WISHLIST;

        if (statusParam != null && !statusParam.isBlank()) {
            try {
                status = Status.valueOf(statusParam.trim().toUpperCase());
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

        Status status = Status.WISHLIST;

        if (statusParam != null && !statusParam.isBlank()) {
            try {
                status = Status.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "invalid status"))
                        .build());
            }
        }

        return libraryEntryRepository.countByUserIdAndStatus(user.id, status);
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

        Status targetStatus = entry.status;
        boolean statusProvided = rawStatus != null && !rawStatus.isBlank();

        if (statusProvided) {
            try {
                targetStatus = Status.valueOf(rawStatus.trim().toUpperCase());
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
            if (targetStatus == Status.COMPLETED && entry.rating == null) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "rating required for COMPLETED"))
                        .build());
            }
            if (targetStatus == Status.COMPLETED) {
                targetRating = entry.rating;
            } else if (targetStatus == Status.WISHLIST) {
                targetRating = null;
            } else {
                targetRating = entry.rating;
            }
        }

        validateRatingForStatus(targetStatus, targetRating);

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

    private void validateRatingForStatus(Status status, Integer rating) {
        if (status == Status.COMPLETED) {
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
        } else if (status == Status.WISHLIST) {
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

        libraryEntryRepository.delete(entry);
    }
}
