package org.hlopes.catalog.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSearchResponse(
        int page,
        List<TmdbSearchResult> results,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("total_results") int totalResults) {}
