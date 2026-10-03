package org.hlopes.catalog.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MediaItemDto(
        UUID id,
        Long externalId,
        String mediaType,
        String title,
        String synopsis,
        String posterPath,
        String backdropPath,
        LocalDate releaseDate) {}
