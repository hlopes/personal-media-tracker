package org.hlopes.catalog.dto;

import java.util.List;

public record EnrichedSeasonDto(SeasonDto season, List<EnrichedEpisodeDto> episodes, long watchedCount) {}
