package org.hlopes.aiinfusion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TavilySearchResponse(String query, List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String title, String url, String content, Double score) {}
}
