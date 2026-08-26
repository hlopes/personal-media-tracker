package org.hlopes;

import java.time.LocalDate;

import org.hlopes.entity.MediaItem;
import org.hlopes.entity.MediaType;
import org.hlopes.entity.TvEpisode;
import org.hlopes.entity.TvSeason;
import org.hlopes.repository.MediaItemRepository;
import org.hlopes.repository.TvEpisodeRepository;
import org.hlopes.repository.TvSeasonRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestDataHelper {

    @Inject
    MediaItemRepository mediaItemRepository;

    @Inject
    TvSeasonRepository tvSeasonRepository;

    @Inject
    TvEpisodeRepository tvEpisodeRepository;

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

    @Transactional
    public TvSeason createTvSeason(MediaItem mediaItem, int seasonNumber, String name) {
        TvSeason season = new TvSeason();
        season.mediaItem = mediaItem;
        season.seasonNumber = seasonNumber;
        season.name = name;
        season.overview = "Overview for " + name;
        season.posterPath = "/season-" + seasonNumber + ".jpg";
        season.airDate = LocalDate.of(2020, 1, 1);
        season.episodeCount = 0;
        tvSeasonRepository.persist(season);
        return season;
    }

    @Transactional
    public TvEpisode createTvEpisode(TvSeason season, int episodeNumber, String title) {
        TvEpisode ep = new TvEpisode();
        ep.season = season;
        ep.mediaItem = season.mediaItem;
        ep.seasonNumber = season.seasonNumber;
        ep.episodeNumber = episodeNumber;
        ep.title = title;
        ep.synopsis = "Synopsis for " + title;
        ep.stillPath = "/still-" + season.seasonNumber + "-" + episodeNumber + ".jpg";
        ep.airDate = LocalDate.of(2020, 1, 1);
        ep.runtime = 45;
        tvEpisodeRepository.persist(ep);
        season.episodeCount = tvEpisodeRepository.findBySeasonId(season.id).size();
        return ep;
    }

    @Transactional
    public TvSeason createTvSeasonWithEpisodes(MediaItem mediaItem, int seasonNumber, String name, int episodeCount) {
        TvSeason season = createTvSeason(mediaItem, seasonNumber, name);
        for (int i = 1; i <= episodeCount; i++) {
            createTvEpisode(season, i, "Episode " + i + " of " + name);
        }
        return season;
    }
}
