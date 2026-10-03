package org.hlopes.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSeasonSummary(
        Long id,
        String name,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("episode_count") Integer episodeCount,
        @JsonProperty("air_date") String airDate) {}
