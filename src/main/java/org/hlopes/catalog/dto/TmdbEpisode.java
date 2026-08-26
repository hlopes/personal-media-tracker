package org.hlopes.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbEpisode(
        Long id,
        String name,
        String overview,
        @JsonProperty("episode_number") Integer episodeNumber,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("still_path") String stillPath,
        @JsonProperty("air_date") String airDate,
        Integer runtime) {}
