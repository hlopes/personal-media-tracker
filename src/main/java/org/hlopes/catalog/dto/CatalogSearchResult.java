package org.hlopes.catalog.dto;

public record CatalogSearchResult(
        Long externalId, String mediaType, String title, String posterPath, String releaseDate, String overview) {}
