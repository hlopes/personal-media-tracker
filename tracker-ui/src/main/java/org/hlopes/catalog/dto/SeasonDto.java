package org.hlopes.catalog.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SeasonDto(
        UUID id,
        int seasonNumber,
        String name,
        String overview,
        String posterPath,
        LocalDate airDate,
        Integer episodeCount) {}
