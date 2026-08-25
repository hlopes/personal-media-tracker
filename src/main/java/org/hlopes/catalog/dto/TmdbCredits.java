package org.hlopes.catalog.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbCredits(Long id, List<TmdbCastMember> cast, List<TmdbCrewMember> crew) {}
