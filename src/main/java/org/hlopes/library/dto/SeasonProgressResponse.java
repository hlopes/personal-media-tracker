package org.hlopes.library.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SeasonProgressResponse(
        UUID id,
        int seasonNumber,
        String name,
        String overview,
        String posterPath,
        LocalDate airDate,
        int episodeCount,
        long watchedCount,
        long totalEpisodes) {}
