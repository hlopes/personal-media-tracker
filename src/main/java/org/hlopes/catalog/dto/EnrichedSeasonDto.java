package org.hlopes.catalog.dto;

import java.time.Instant;
import java.util.List;

public record EnrichedSeasonDto(
        SeasonDto season, List<EnrichedEpisodeDto> episodes, boolean watched, Integer rating, Instant watchedAt) {}
