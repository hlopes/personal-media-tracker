package org.hlopes.aiinfusion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TavilySearchRequest(
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("query") String query,
        @JsonProperty("search_depth") String searchDepth,
        @JsonProperty("max_results") int maxResults,
        @JsonProperty("include_domains") List<String> includeDomains) {}
