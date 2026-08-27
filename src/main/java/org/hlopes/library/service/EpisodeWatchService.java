package org.hlopes.library.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hlopes.auth.entity.User;
import org.hlopes.auth.repository.UserRepository;
import org.hlopes.catalog.entity.MediaTypeEnum;
import org.hlopes.catalog.entity.TvEpisode;
import org.hlopes.catalog.entity.TvSeason;
import org.hlopes.catalog.repository.TvEpisodeRepository;
import org.hlopes.catalog.repository.TvSeasonRepository;
import org.hlopes.library.dto.EpisodeWatchResponse;
import org.hlopes.library.dto.EpisodeWithWatchResponse;
import org.hlopes.library.dto.SeasonProgressResponse;
import org.hlopes.library.entity.EpisodeWatch;
import org.hlopes.library.entity.LibraryEntry;
import org.hlopes.library.entity.StatusEnum;
import org.hlopes.library.repository.EpisodeWatchRepository;
import org.hlopes.library.repository.LibraryEntryRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class EpisodeWatchService {

    @Inject
    UserRepository userRepository;

    @Inject
    LibraryEntryRepository libraryEntryRepository;

    @Inject
    TvSeasonRepository tvSeasonRepository;

    @Inject
    TvEpisodeRepository tvEpisodeRepository;

    @Inject
    EpisodeWatchRepository episodeWatchRepository;

    public List<SeasonProgressResponse> getSeasonsProgress(String email, UUID libraryEntryId) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        List<TvSeason> seasons = tvSeasonRepository.findByMediaItemId(entry.mediaItem.id);
        List<SeasonProgressResponse> result = new ArrayList<>();

        for (TvSeason s : seasons) {
            List<TvEpisode> eps =
                    tvEpisodeRepository.findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, s.seasonNumber);
            long total = eps.size();
            long watched = episodeWatchRepository.countByUserIdAndSeasonId(user.id, s.id);
            // for progress header we still show total as eps.size() but watched accordingly
            // For specials, progress not affecting series total, but per-season still shows watched/total
            result.add(new SeasonProgressResponse(
                    s.id, s.seasonNumber, s.name, s.overview, s.posterPath, s.airDate, eps.size(), watched, total));
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

    public List<EpisodeWithWatchResponse> getEpisodesWithWatch(String email, UUID libraryEntryId, int seasonNumber) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        tvSeasonRepository
                .findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "season not found"))
                        .build()));
        List<TvEpisode> episodes =
                tvEpisodeRepository.findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber);
        List<EpisodeWithWatchResponse> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (TvEpisode ep : episodes) {
            var watchOpt = episodeWatchRepository.findByUserIdAndEpisodeId(user.id, ep.id);
            boolean watched = watchOpt.isPresent();
            Integer rating = watchOpt.map(w -> w.rating).orElse(null);
            boolean future = ep.airDate != null && ep.airDate.isAfter(today);
            result.add(new EpisodeWithWatchResponse(
                    ep.id,
                    ep.seasonNumber,
                    ep.episodeNumber,
                    ep.title,
                    ep.synopsis,
                    ep.stillPath,
                    ep.airDate,
                    ep.runtime,
                    watched,
                    rating,
                    future));
        }
        result.sort((a, b) -> Integer.compare(a.episodeNumber(), b.episodeNumber()));

        return result;
    }

    @Transactional
    public EpisodeWatchResponse watchEpisode(String email, UUID libraryEntryId, UUID episodeId, Integer rating) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        TvEpisode episode = tvEpisodeRepository.findById(episodeId);

        if (episode == null || !episode.mediaItem.id.equals(entry.mediaItem.id)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "episode not found"))
                    .build());
        }
        checkAired(episode);
        validateRating(rating);

        var existingOpt = episodeWatchRepository.findByUserIdAndEpisodeId(user.id, episode.id);
        EpisodeWatch watch;

        if (existingOpt.isPresent()) {
            watch = existingOpt.get();

            if (rating != null) {
                watch.rating = rating;
            }
            // update watchedAt on re-mark
            watch.watchedAt = Instant.now();
            episodeWatchRepository.persist(watch);
        } else {
            watch = new EpisodeWatch();
            watch.user = user;
            watch.episode = episode;
            watch.rating = rating;
            watch.watchedAt = Instant.now();
            episodeWatchRepository.persist(watch);
        }
        deriveStatusAndPersist(entry);

        return new EpisodeWatchResponse(watch.id, episode.id, watch.rating, watch.watchedAt);
    }

    @Transactional
    public EpisodeWatchResponse updateEpisodeWatch(String email, UUID libraryEntryId, UUID episodeId, Integer rating) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        TvEpisode episode = tvEpisodeRepository.findById(episodeId);

        if (episode == null || !episode.mediaItem.id.equals(entry.mediaItem.id)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "episode not found"))
                    .build());
        }
        if (rating == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "rating must be between 1 and 5"))
                    .build());
        }
        validateRating(rating);
        EpisodeWatch watch = episodeWatchRepository
                .findByUserIdAndEpisodeId(user.id, episode.id)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "episode watch not found"))
                        .build()));
        watch.rating = rating;
        episodeWatchRepository.persist(watch);
        // rating update does not change status but we still ensure consistency
        deriveStatusAndPersist(entry);

        return new EpisodeWatchResponse(watch.id, episode.id, watch.rating, watch.watchedAt);
    }

    @Transactional
    public void unwatchEpisode(String email, UUID libraryEntryId, UUID episodeId) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        TvEpisode episode = tvEpisodeRepository.findById(episodeId);

        if (episode == null || !episode.mediaItem.id.equals(entry.mediaItem.id)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "episode not found"))
                    .build());
        }
        EpisodeWatch watch = episodeWatchRepository
                .findByUserIdAndEpisodeId(user.id, episode.id)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "episode watch not found"))
                        .build()));
        episodeWatchRepository.delete(watch);
        deriveStatusAndPersist(entry);
    }

    @Transactional
    public List<EpisodeWatchResponse> watchSeason(String email, UUID libraryEntryId, int seasonNumber, Integer rating) {
        User user = requireUser(email);
        LibraryEntry entry = requireLibraryEntry(user, libraryEntryId);
        tvSeasonRepository
                .findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber)
                .orElseThrow(() -> new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "season not found"))
                        .build()));
        List<TvEpisode> episodes =
                tvEpisodeRepository.findByMediaItemIdAndSeasonNumber(entry.mediaItem.id, seasonNumber);

        if (episodes.isEmpty()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "season has no episodes"))
                    .build());
        }
        validateRating(rating);
        // check any unaired

        for (TvEpisode ep : episodes) {
            if (ep.airDate != null && ep.airDate.isAfter(LocalDate.now())) {
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "season contains unaired episodes"))
                        .build());
            }
        }
        List<EpisodeWatchResponse> result = new ArrayList<>();

        for (TvEpisode ep : episodes) {
            var existingOpt = episodeWatchRepository.findByUserIdAndEpisodeId(user.id, ep.id);
            EpisodeWatch watch;

            if (existingOpt.isPresent()) {
                watch = existingOpt.get();

                if (rating != null) {
                    watch.rating = rating;
                    episodeWatchRepository.persist(watch);
                }
            } else {
                watch = new EpisodeWatch();
                watch.user = user;
                watch.episode = ep;
                watch.rating = rating;
                watch.watchedAt = Instant.now();
                episodeWatchRepository.persist(watch);
            }
            result.add(new EpisodeWatchResponse(watch.id, ep.id, watch.rating, watch.watchedAt));
        }
        deriveStatusAndPersist(entry);

        return result;
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
                    .entity(Map.of("error", "episode watch only for TV Series"))
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

    private void checkAired(TvEpisode episode) {
        if (episode.airDate != null && episode.airDate.isAfter(LocalDate.now())) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "episode has not aired yet"))
                    .build());
        }
    }

    private void deriveStatusAndPersist(LibraryEntry entry) {
        UUID mediaItemId = entry.mediaItem.id;
        // total excluding specials
        List<TvEpisode> all = tvEpisodeRepository.findByMediaItemId(mediaItemId);
        long total = all.stream().filter(e -> e.seasonNumber != 0).count();

        if (total == 0) {
            return;
        }
        long watched = episodeWatchRepository.countByUserIdAndMediaItemId(entry.user.id, mediaItemId);
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
            // for IN_PROGRESS and COMPLETED keep existing rating (COMPLETED may have null for granular)
            libraryEntryRepository.persist(entry);
        }
    }
}
