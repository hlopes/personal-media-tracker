package org.hlopes.catalog.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hlopes.catalog.client.TmdbClient;
import org.hlopes.catalog.dto.EpisodeDto;
import org.hlopes.catalog.dto.SeasonDto;
import org.hlopes.catalog.dto.SeasonWithEpisodesDto;
import org.hlopes.catalog.dto.TmdbEpisode;
import org.hlopes.catalog.dto.TmdbSeasonDetails;
import org.hlopes.catalog.dto.TmdbSeasonSummary;
import org.hlopes.catalog.dto.TmdbTvDetails;
import org.hlopes.catalog.entity.MediaItem;
import org.hlopes.catalog.entity.TvEpisode;
import org.hlopes.catalog.entity.TvSeason;
import org.hlopes.catalog.repository.TvEpisodeRepository;
import org.hlopes.catalog.repository.TvSeasonRepository;
import org.hlopes.config.ApplicationConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TvSeasonService {

    @Inject
    @RestClient
    TmdbClient tmdbClient;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    TvSeasonRepository seasonRepository;

    @Inject
    TvEpisodeRepository episodeRepository;

    private static final Duration STALENESS = Duration.ofHours(24);

    @Transactional
    public void syncFromTvDetails(MediaItem mediaItem, TmdbTvDetails details) {
        if (details == null || details.seasons() == null || details.seasons().isEmpty()) {
            return;
        }

        String apiKey = applicationConfig.tmdb().apiKey();

        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        for (TmdbSeasonSummary summary : details.seasons()) {
            if (summary == null || summary.seasonNumber() == null) {
                continue;
            }

            int seasonNumber = summary.seasonNumber();
            TvSeason season = seasonRepository
                    .findByMediaItemIdAndSeasonNumber(mediaItem.id, seasonNumber)
                    .orElseGet(() -> {
                        TvSeason s = new TvSeason();
                        s.mediaItem = mediaItem;
                        s.seasonNumber = seasonNumber;
                        return s;
                    });
            season.name = summary.name();
            season.overview = summary.overview();
            season.posterPath = summary.posterPath();
            season.airDate = parseDate(summary.airDate());
            season.episodeCount = summary.episodeCount();

            if (season.id == null) {
                seasonRepository.persist(season);
            }

            try {
                TmdbSeasonDetails seasonDetails = tmdbClient.getSeason(details.id(), seasonNumber, apiKey, "en-US");

                if (seasonDetails != null && seasonDetails.episodes() != null) {
                    for (TmdbEpisode epDto : seasonDetails.episodes()) {
                        if (epDto == null || epDto.episodeNumber() == null || epDto.seasonNumber() == null) {
                            continue;
                        }

                        TvEpisode existing = findEpisode(season.id, epDto.episodeNumber());
                        TvEpisode ep = existing != null ? existing : new TvEpisode();

                        if (ep.id == null) {
                            ep.season = season;
                            ep.mediaItem = mediaItem;
                            ep.seasonNumber = epDto.seasonNumber();
                            ep.episodeNumber = epDto.episodeNumber();
                        }

                        ep.title = epDto.name() != null ? epDto.name() : "Episode " + epDto.episodeNumber();
                        ep.synopsis = epDto.overview();
                        ep.stillPath = epDto.stillPath();
                        ep.airDate = parseDate(epDto.airDate());
                        ep.runtime = epDto.runtime();

                        if (ep.id == null) {
                            episodeRepository.persist(ep);
                        }
                    }

                    if (seasonDetails.episodes() != null) {
                        season.episodeCount = seasonDetails.episodes().size();
                    }
                }
            } catch (Exception ignored) {
                // per-season failure should not abort other seasons
            }
        }
    }

    @Transactional
    public void syncIfStale(MediaItem mediaItem) {
        List<TvSeason> cached = seasonRepository.findByMediaItemId(mediaItem.id);

        if (cached.isEmpty()) {
            trySyncFresh(mediaItem);

            return;
        }

        Instant now = Instant.now();
        boolean stale = cached.stream()
                .anyMatch(s -> s.updatedAt == null
                        || Duration.between(s.updatedAt, now).compareTo(STALENESS) > 0);

        if (stale) {
            trySyncFresh(mediaItem);
        }
    }

    public List<SeasonWithEpisodesDto> getSeasonsWithEpisodes(UUID mediaItemId) {
        List<TvSeason> seasons = seasonRepository.findByMediaItemId(mediaItemId);
        List<SeasonWithEpisodesDto> result = new ArrayList<>();

        for (TvSeason s : seasons) {
            List<TvEpisode> episodes = episodeRepository.findBySeasonId(s.id);
            SeasonDto seasonDto =
                    new SeasonDto(s.id, s.seasonNumber, s.name, s.overview, s.posterPath, s.airDate, s.episodeCount);
            List<EpisodeDto> episodeDtos = episodes.stream()
                    .map(e -> new EpisodeDto(
                            e.id,
                            e.seasonNumber,
                            e.episodeNumber,
                            e.title,
                            e.synopsis,
                            e.stillPath,
                            e.airDate,
                            e.runtime))
                    .toList();
            result.add(new SeasonWithEpisodesDto(seasonDto, episodeDtos));
        }

        result.sort((a, b) -> {
            int sa = a.season().seasonNumber();
            int sb = b.season().seasonNumber();

            // specials (0) at bottom

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

    private void trySyncFresh(MediaItem mediaItem) {
        String apiKey = applicationConfig.tmdb().apiKey();

        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        try {
            TmdbTvDetails details = tmdbClient.getTv(mediaItem.externalId, apiKey, "en-US");

            if (details != null) {
                syncFromTvDetails(mediaItem, details);
            }
        } catch (Exception ignored) {
        }
    }

    private TvEpisode findEpisode(UUID seasonId, int episodeNumber) {
        return episodeRepository
                .find("season.id = ?1 and episodeNumber = ?2", seasonId, episodeNumber)
                .firstResult();
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
