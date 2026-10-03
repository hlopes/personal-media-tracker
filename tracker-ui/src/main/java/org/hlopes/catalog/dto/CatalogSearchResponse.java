package org.hlopes.catalog.dto;

import java.util.List;

public record CatalogSearchResponse(List<CatalogSearchResult> results, int page, int total) {}
