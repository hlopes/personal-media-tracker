package org.hlopes.library.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hlopes.auth.entity.User;
import org.hlopes.auth.repository.UserRepository;
import org.hlopes.catalog.dto.EpisodeDto;
import org.hlopes.catalog.entity.MediaTypeEnum;
import org.hlopes.catalog.entity.TvSeason;
import org.hlopes.catalog.repository.TvEpisodeRepository;
import org.hlopes.catalog.repository.TvSeasonRepository;
import org.hlopes.library.dto.SeasonProgressResponse;
import org.hlopes.library.dto.SeasonWatchResponse;
import org.hlopes.library.entity.LibraryEntry;
import org.hlopes.library.entity.SeasonWatch;
import org.hlopes.library.entity.StatusEnum;
import org.hlopes.library.repository.LibraryEntryRepository;
import org.hlopes.library.repository.SeasonWatchRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class SeasonWatchService {

    @Inject
    UserRepository userRepository;

    @Inject
    LibraryEntryRepository libraryEntryRepository;

    @Inject
    TvSeasonRepository tvSeasonRepository;

    @Inject
    TvEpisodeRepository tvEpisodeRepository;

    @Inject
    SeasonWatchRepository seasonWatchRepository;

    public List<SeasonProgressResponse> getSeasonsProgress(String email, UUID libraryEntryId) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        List<TvSeason> seasons = tvSeasonRepository.findByMediaItemId(entry.mediaItem.id);
        List<SeasonProgressResponse> result = new ArrayList<>();

        for (TvSeason s : seasons) {
            var watchOpt = seasonWatchRepository.findByUserIdAndSeasonId(user.id, s.id);
            boolean watched = watchOpt.isPresent();
            Integer rating = watchOpt.map(w -> w.rating).orElse(null);
            Instant watchedAt = watchOpt.map(w -> w.watchedAt).orElse(null);
            int episodeCount = s.episodeCount != null
                    ? s.episodeCount
                    : tvEpisodeRepository.findBySeasonId(s.id).size();
            result.add(new SeasonProgressResponse(
                    s.id,
                    s.seasonNumber,
                    s.name,
                    s.overview,
                    s.posterPath,
                    s.airDate,
                    episodeCount,
                    watched,
                    rating,
                    watchedAt));
        }
        result.sort((a, b) -> {
            int sa = a.seasonNumber();
            int sb = b.seasonNumber();

            if (sa == 0 && sb != 0) {
                return 1;
            }

            if (sb == 0 && sa != 0) {
                return -1;
            }

            return Integer.compare(sa, sb);
        });

        return result;
    }

    public List<EpisodeDto> getSeasonEpisodes(String email, UUID libraryEntryId, int seasonNumber) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        tvSeasonRepository
                .findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "season not found"))
                        .build()));
        var episodes = tvEpisodeRepository.findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber);
        List<EpisodeDto> result = new ArrayList<>();

        for (var ep : episodes) {
            result.add(new EpisodeDto(
                    ep.id,
                    ep.seasonNumber,
                    ep.episodeNumber,
                    ep.title,
                    ep.synopsis,
                    ep.stillPath,
                    ep.airDate,
                    ep.runtime));
        }
        result.sort((a, b) -> Integer.compare(a.episodeNumber(), b.episodeNumber()));
        return result;
    }

    @Transactional
    public SeasonWatchResponse watchSeason(String email, UUID libraryEntryId, int seasonNumber, Integer rating) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        TvSeason season = tvSeasonRepository
                .findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "season not found"))
                        .build()));

        if (season.seasonNumber != 0) {
            // check unaired: season airDate future or any episode airDate future

            if (season.airDate != null && season.airDate.isAfter(LocalDate.now())) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "season has not aired yet"))
                        .build());
            }
            var episodes = tvEpisodeRepository.findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber);

            for (var ep : episodes) {
                if (ep.airDate != null && ep.airDate.isAfter(LocalDate.now())) {
                    throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "season contains unaired episodes"))
                            .build());
                }
            }

            if (episodes.isEmpty() && season.episodeCount != null && season.episodeCount == 0) {
                // allow watching empty season? treat as not found
            }
        } else {
            // specials: allow watching but it won't count toward status; still apply unaired guard similarly

            if (season.airDate != null && season.airDate.isAfter(LocalDate.now())) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "season has not aired yet"))
                        .build());
            }
            var episodes = tvEpisodeRepository.findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber);

            for (var ep : episodes) {
                if (ep.airDate != null && ep.airDate.isAfter(LocalDate.now())) {
                    throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "season contains unaired episodes"))
                            .build());
                }
            }
        }

        validateRating(rating);

        var existingOpt = seasonWatchRepository.findByUserIdAndSeasonId(user.id, season.id);
        SeasonWatch watch;

        if (existingOpt.isPresent()) {
            watch = existingOpt.get();

            if (rating != null) {
                watch.rating = rating;
            }

            watch.watchedAt = Instant.now();
            seasonWatchRepository.persist(watch);
        } else {
            watch = new SeasonWatch();
            watch.user = user;
            watch.season = season;
            watch.rating = rating;
            watch.watchedAt = Instant.now();
            seasonWatchRepository.persist(watch);
        }
        deriveStatusAndPersist(entry);

        return new SeasonWatchResponse(watch.id, season.id, watch.rating, watch.watchedAt);
    }

    @Transactional
    public SeasonWatchResponse updateSeasonWatch(String email, UUID libraryEntryId, int seasonNumber, Integer rating) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        TvSeason season = tvSeasonRepository
                .findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "season not found"))
                        .build()));

        if (rating == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "rating must be between 1 and 5"))
                    .build());
        }
        validateRating(rating);
        SeasonWatch watch = seasonWatchRepository
                .findByUserIdAndSeasonId(user.id, season.id)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "season watch not found"))
                        .build()));
        watch.rating = rating;
        seasonWatchRepository.persist(watch);
        deriveStatusAndPersist(entry);

        return new SeasonWatchResponse(watch.id, season.id, watch.rating, watch.watchedAt);
    }

    @Transactional
    public void unwatchSeason(String email, UUID libraryEntryId, int seasonNumber) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        TvSeason season = tvSeasonRepository
                .findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "season not found"))
                        .build()));
        SeasonWatch watch = seasonWatchRepository
                .findByUserIdAndSeasonId(user.id, season.id)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "season watch not found"))
                        .build()));
        seasonWatchRepository.delete(watch);
        deriveStatusAndPersist(entry);
    }

    private User requireUser(String email) {
        String normalized = email == null ? null : email.trim().toLowerCase();

        return userRepository
                .findByEmail(normalized)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build()));
    }

    private LibraryEntry requireLibraryEntry(User user, UUID libraryEntryId) {
        LibraryEntry entry = libraryEntryRepository
                .findByIdAndUserId(libraryEntryId, user.id)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "entry not found"))
                        .build()));

        if (entry.mediaItem == null || entry.mediaItem.mediaType != MediaTypeEnum.TV_SERIES) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "season watch only for TV Series"))
                    .build());
        }
        return entry;
    }

    private void validateRating(Integer rating) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "rating must be between 1 and 5"))
                    .build());
        }
    }

    private void deriveStatusAndPersist(LibraryEntry entry) {
        UUID mediaItemId = entry.mediaItem.id;
        List<TvSeason> seasons = tvSeasonRepository.findByMediaItemId(mediaItemId);
        long total = seasons.stream().filter(s -> s.seasonNumber != 0).count();

        if (total == 0) {
            return;
        }
        long watched = seasonWatchRepository.countByUserIdAndMediaItemId(entry.user.id, mediaItemId);
        StatusEnum newStatus;

        if (watched == 0) {
            newStatus = StatusEnum.WISHLIST;
        } else if (watched >= total) {
            newStatus = StatusEnum.COMPLETED;
        } else {
            newStatus = StatusEnum.IN_PROGRESS;
        }

        if (entry.status != newStatus) {
            entry.status = newStatus;

            if (newStatus == StatusEnum.WISHLIST) {
                entry.rating = null;
            }
            libraryEntryRepository.persist(entry);
        }
    }
}
