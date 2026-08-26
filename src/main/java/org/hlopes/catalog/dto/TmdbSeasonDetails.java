package org.hlopes.catalog.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSeasonDetails(
        @JsonProperty("_id") String internalId,
        Long id,
        String name,
        String overview,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("air_date") String airDate,
        List<TmdbEpisode> episodes) {}
