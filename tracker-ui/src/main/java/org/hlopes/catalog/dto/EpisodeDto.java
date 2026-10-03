package org.hlopes.catalog.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EpisodeDto(
        UUID id,
        int seasonNumber,
        int episodeNumber,
        String title,
        String synopsis,
        String stillPath,
        LocalDate airDate,
        Integer runtime) {}
