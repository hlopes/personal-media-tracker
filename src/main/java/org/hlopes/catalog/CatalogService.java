package org.hlopes.catalog;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hlopes.catalog.dto.CastDto;
import org.hlopes.catalog.dto.CatalogDetailResponse;
import org.hlopes.catalog.dto.CatalogSearchResponse;
import org.hlopes.catalog.dto.CatalogSearchResult;
import org.hlopes.catalog.dto.CreditsDto;
import org.hlopes.catalog.dto.DirectorDto;
import org.hlopes.catalog.dto.MediaItemDto;
import org.hlopes.catalog.dto.TmdbCredits;
import org.hlopes.catalog.dto.TmdbMovieDetails;
import org.hlopes.catalog.dto.TmdbSearchResponse;
import org.hlopes.catalog.dto.TmdbSearchResult;
import org.hlopes.catalog.dto.TmdbTvDetails;
import org.hlopes.config.ApplicationConfig;
import org.hlopes.entity.MediaItem;
import org.hlopes.entity.MediaType;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class CatalogService {

    @Inject
    @RestClient
    TmdbClient tmdbClient;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    MediaItemService mediaItemService;

    @CacheResult(cacheName = "catalog-search")
    public CatalogSearchResponse search(String query, String type, int page) {
        String normalized = query == null ? "" : query.trim();

        if (normalized.length() < 2) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", "query must be at least 2 characters"))
                    .build());
        }

        String effectiveType = type == null || type.isBlank() ? "multi" : type.toLowerCase();

        if (!effectiveType.equals("multi") && !effectiveType.equals("movie") && !effectiveType.equals("tv")) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", "type must be one of multi, movie, tv"))
                    .build());
        }

        String apiKey = applicationConfig.tmdb().apiKey();

        if (apiKey == null || apiKey.isBlank()) {
            return new CatalogSearchResponse(List.of(), page, 0);
        }

        try {
            TmdbSearchResponse tmdbResponse;

            if (effectiveType.equals("movie")) {
                tmdbResponse = tmdbClient.searchMovie(apiKey, normalized, page, "en-US", false);
            } else if (effectiveType.equals("tv")) {
                tmdbResponse = tmdbClient.searchTv(apiKey, normalized, page, "en-US", false);
            } else {
                tmdbResponse = tmdbClient.searchMulti(apiKey, normalized, page, "en-US", false);
            }

            if (tmdbResponse == null || tmdbResponse.results() == null) {
                return new CatalogSearchResponse(List.of(), page, 0);
            }

            List<CatalogSearchResult> mapped = tmdbResponse.results().stream()
                    .filter(r -> {
                        if (effectiveType.equals("multi")) {
                            return "movie".equals(r.mediaType()) || "tv".equals(r.mediaType());
                        }

                        return true;
                    })
                    .limit(10)
                    .map(r -> mapToCatalogResult(r, effectiveType))
                    .collect(Collectors.toList());

            return new CatalogSearchResponse(mapped, tmdbResponse.page(), tmdbResponse.totalResults());

        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException(Response.status(502)
                    .entity(java.util.Map.of("error", "catalog unavailable"))
                    .build());
        }
    }

    @CacheResult(cacheName = "catalog-detail")
    public CatalogDetailResponse detail(String rawMediaType, Long externalId) {
        if (externalId == null) {
            throw new WebApplicationException(Response.status(400)
                    .entity(java.util.Map.of("error", "externalId is required"))
                    .build());
        }

        String normalized = rawMediaType == null ? "" : rawMediaType.trim().toLowerCase();
        MediaType mediaType;

        if (normalized.equals("movie")) {
            mediaType = MediaType.MOVIE;
        } else if (normalized.equals("tv") || normalized.equals("tv_series") || normalized.equals("tv-series")) {
            mediaType = MediaType.TV_SERIES;
        } else {
            throw new WebApplicationException(Response.status(400)
                    .entity(java.util.Map.of("error", "mediaType must be movie or tv"))
                    .build());
        }

        String apiKey = applicationConfig.tmdb().apiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new WebApplicationException(Response.status(502)
                    .entity(java.util.Map.of("error", "catalog unavailable - missing api key"))
                    .build());
        }

        try {
            MediaItem mediaItem;
            TmdbCredits credits;
            DirectorDto director = null;

            if (mediaType == MediaType.MOVIE) {
                TmdbMovieDetails details = tmdbClient.getMovie(externalId, apiKey, "en-US");

                if (details == null || details.id() == null) {
                    throw new WebApplicationException(Response.status(404)
                            .entity(java.util.Map.of("error", "media not found"))
                            .build());
                }

                mediaItem = mediaItemService.findOrCreateFromMovie(details);
                credits = tmdbClient.getMovieCredits(externalId, apiKey, "en-US");

                if (credits != null && credits.crew() != null) {
                    director = credits.crew().stream()
                            .filter(c -> "Director".equals(c.job()))
                            .findFirst()
                            .map(c -> new DirectorDto(c.id(), c.name()))
                            .orElse(null);
                }
            } else {
                TmdbTvDetails details = tmdbClient.getTv(externalId, apiKey, "en-US");

                if (details == null || details.id() == null) {
                    throw new WebApplicationException(Response.status(404)
                            .entity(java.util.Map.of("error", "media not found"))
                            .build());
                }

                mediaItem = mediaItemService.findOrCreateFromTv(details);
                credits = tmdbClient.getTvCredits(externalId, apiKey, "en-US");

                if (details.createdBy() != null && !details.createdBy().isEmpty()) {
                    var creator = details.createdBy().get(0);
                    director = new DirectorDto(creator.id(), creator.name());
                }

                if (director == null && credits != null && credits.crew() != null) {
                    director = credits.crew().stream()
                            .filter(c -> "Director".equals(c.job()))
                            .findFirst()
                            .map(c -> new DirectorDto(c.id(), c.name()))
                            .orElse(null);
                }
            }

            List<CastDto> cast = List.of();

            if (credits != null && credits.cast() != null) {
                cast = credits.cast().stream()
                        .sorted((a, b) -> Integer.compare(
                                a.order() == null ? 999 : a.order(), b.order() == null ? 999 : b.order()))
                        .limit(10)
                        .map(c -> new CastDto(c.id(), c.name(), c.character(), c.profilePath()))
                        .collect(Collectors.toList());
            }

            MediaItemDto mediaItemDto = new MediaItemDto(
                    mediaItem.id,
                    mediaItem.externalId,
                    mediaItem.mediaType.name(),
                    mediaItem.title,
                    mediaItem.synopsis,
                    mediaItem.posterPath,
                    mediaItem.backdropPath,
                    mediaItem.releaseDate);

            CreditsDto creditsDto = new CreditsDto(cast, director);

            return new CatalogDetailResponse(mediaItemDto, creditsDto);

        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                throw new WebApplicationException(Response.status(404)
                        .entity(java.util.Map.of("error", "media not found"))
                        .build());
            }

            throw new WebApplicationException(Response.status(502)
                    .entity(java.util.Map.of("error", "catalog unavailable"))
                    .build());
        }
    }

    private CatalogSearchResult mapToCatalogResult(TmdbSearchResult r, String effectiveType) {
        String mediaType;

        if (effectiveType.equals("movie")) {
            mediaType = "MOVIE";
        } else if (effectiveType.equals("tv")) {
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
