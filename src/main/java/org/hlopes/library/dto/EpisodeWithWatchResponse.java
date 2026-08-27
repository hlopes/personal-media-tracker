package org.hlopes.library.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EpisodeWithWatchResponse(
        UUID id,
        int seasonNumber,
        int episodeNumber,
        String title,
        String synopsis,
        String stillPath,
        LocalDate airDate,
        Integer runtime,
        boolean watched,
        Integer rating,
        boolean airDateInFuture) {}
