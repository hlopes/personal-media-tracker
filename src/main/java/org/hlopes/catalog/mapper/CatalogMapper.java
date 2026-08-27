package org.hlopes.catalog.mapper;

import org.hlopes.catalog.dto.CatalogSearchResult;
import org.hlopes.catalog.dto.TmdbSearchResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface CatalogMapper {

    default CatalogSearchResult mapToCatalogResult(TmdbSearchResult r, String effectiveType) {
        if (r == null) {
            return null;
        }

        String mediaType;

        if ("movie".equals(effectiveType)) {
            mediaType = "MOVIE";
        } else if ("tv".equals(effectiveType)) {
            mediaType = "TV_SERIES";
        } else {
            if ("movie".equals(r.mediaType())) {
                mediaType = "MOVIE";
            } else if ("tv".equals(r.mediaType())) {
                mediaType = "TV_SERIES";
            } else {
                mediaType = "MOVIE";
            }
        }

        String title = r.title() != null && !r.title().isBlank() ? r.title() : r.name();
        String releaseDate = r.releaseDate() != null && !r.releaseDate().isBlank() ? r.releaseDate() : r.firstAirDate();

        return new CatalogSearchResult(r.id(), mediaType, title, r.posterPath(), releaseDate, r.overview());
    }
}
