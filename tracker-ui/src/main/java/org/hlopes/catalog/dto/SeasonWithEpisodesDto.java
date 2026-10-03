package org.hlopes.catalog.dto;

import java.util.List;

public record SeasonWithEpisodesDto(SeasonDto season, List<EpisodeDto> episodes) {}
