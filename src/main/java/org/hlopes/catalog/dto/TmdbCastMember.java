package org.hlopes.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbCastMember(
        Long id, String name, String character, @JsonProperty("profile_path") String profilePath, Integer order) {}
