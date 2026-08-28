package org.hlopes.catalog.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.auth.service.AuthService;
import org.hlopes.catalog.dto.EnrichedEpisodeDto;
import org.hlopes.catalog.dto.EnrichedSeasonDto;
import org.hlopes.catalog.dto.MediaDetailEnrichedResponse;
import org.hlopes.catalog.dto.MediaItemDto;
import org.hlopes.catalog.dto.SeasonWithEpisodesDto;
import org.hlopes.catalog.entity.MediaTypeEnum;
import org.hlopes.catalog.repository.MediaItemRepository;
import org.hlopes.catalog.service.CatalogService;
import org.hlopes.catalog.service.TvSeasonService;
import org.hlopes.config.ApplicationConfig;
import org.hlopes.library.entity.SeasonWatch;
import org.hlopes.library.entity.StatusEnum;
import org.hlopes.library.repository.LibraryEntryRepository;
import org.hlopes.library.repository.SeasonWatchRepository;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/media")
@Produces(MediaType.APPLICATION_JSON)
public class MediaDetailResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    AuthService authService;

    @Inject
    CatalogService catalogService;

    @Inject
    TvSeasonService tvSeasonService;

    @Inject
    LibraryEntryRepository libraryEntryRepository;

    @Inject
    MediaItemRepository mediaItemRepository;

    @Inject
    SeasonWatchRepository seasonWatchRepository;

    @GET
    @Path("{type}/{id}")
    @RolesAllowed("User")
    public MediaDetailEnrichedResponse getEnriched(@PathParam("type") String type, @PathParam("id") Long id) {
        String email = jwt != null ? jwt.getSubject() : null;

        if (email == null || email.isBlank()) {
            throw new NotAuthorizedException("Not logged in");
        }

        try {
            var detail = catalogService.detail(type, id);
            return buildResponse(
                    detail.mediaItem(),
                    detail.credits().cast(),
                    detail.credits().director(),
                    email);
        } catch (NotFoundException e) {
            throw e;
        } catch (WebApplicationException e) {
            try {
                String normalized = type == null ? "" : type.trim().toLowerCase();
                MediaTypeEnum mt = null;

                if (normalized.equals("movie")) {
                    mt = MediaTypeEnum.MOVIE;
                } else if (normalized.equals("tv")
                        || normalized.equals("tv_series")
                        || normalized.equals("tv-series")) {
                    mt = MediaTypeEnum.TV_SERIES;
                }

                if (mt != null) {
                    var mediaOpt = mediaItemRepository.findByExternalIdAndMediaType(id, mt);

                    if (mediaOpt.isPresent()) {
                        var mediaItem = mediaOpt.get();
                        String imageBase = applicationConfig.tmdb().imageBaseUrl();
                        String posterUrl =
                                mediaItem.posterPath != null ? imageBase + "/w500" + mediaItem.posterPath : null;
                        String backdropUrl =
                                mediaItem.backdropPath != null ? imageBase + "/w1280" + mediaItem.backdropPath : null;

                        var mediaItemDto = new MediaItemDto(
                                mediaItem.id,
                                mediaItem.externalId,
                                mediaItem.mediaType.name(),
                                mediaItem.title,
                                mediaItem.synopsis,
                                mediaItem.posterPath,
                                mediaItem.backdropPath,
                                mediaItem.releaseDate);

                        var user = authService.getUserOrNull(email);
                        boolean alreadyInWishlist = false;
                        boolean alreadyInWatched = false;
                        String currentStatus = null;
                        Integer currentRating = null;
                        UUID currentEntryId = null;

                        if (user != null) {
                            var opt = libraryEntryRepository.findByUserIdAndMediaItemId(user.id, mediaItem.id);

                            if (opt.isPresent()) {
                                var en = opt.get();
                                currentStatus = en.status.name();
                                currentRating = en.rating;
                                currentEntryId = en.id;
                                alreadyInWishlist = en.status == StatusEnum.WISHLIST;
                                alreadyInWatched = en.status == StatusEnum.COMPLETED;
                            }
                        }

                        List<SeasonWithEpisodesDto> rawSeasons = List.of();

                        try {
                            rawSeasons = tvSeasonService.getSeasonsWithEpisodes(mediaItem.id);
                        } catch (Exception ignored) {
                        }

                        List<EnrichedSeasonDto> seasons = buildEnrichedSeasons(rawSeasons, mediaItemDto, email);

                        return new MediaDetailEnrichedResponse(
                                mediaItemDto,
                                List.of(),
                                null,
                                posterUrl,
                                backdropUrl,
                                imageBase,
                                alreadyInWishlist,
                                alreadyInWatched,
                                currentStatus,
                                currentRating,
                                currentEntryId,
                                seasons);
                    }
                }
            } catch (Exception ignored) {
            }

            if (e.getResponse().getStatus() == 404) {
                throw new NotFoundException("Media not found");
            }

            throw e;
        } catch (Exception e) {
            throw new WebApplicationException(Response.status(404)
                    .entity(Map.of("error", "media not found"))
                    .build());
        }
    }

    private MediaDetailEnrichedResponse buildResponse(
            MediaItemDto mediaItemDto,
            List<org.hlopes.catalog.dto.CastDto> castRaw,
            org.hlopes.catalog.dto.DirectorDto directorRaw,
            String email) {
        String imageBase = applicationConfig.tmdb().imageBaseUrl();
        String posterUrl = mediaItemDto.posterPath() != null ? imageBase + "/w500" + mediaItemDto.posterPath() : null;
        String backdropUrl =
                mediaItemDto.backdropPath() != null ? imageBase + "/w1280" + mediaItemDto.backdropPath() : null;

        var user = authService.getUserOrNull(email);
        boolean alreadyInWishlist = false;
        boolean alreadyInWatched = false;
        String currentStatus = null;
        Integer currentRating = null;
        UUID currentEntryId = null;

        if (user != null) {
            var opt = libraryEntryRepository.findByUserIdAndMediaItemId(user.id, mediaItemDto.id());

            if (opt.isPresent()) {
                var e = opt.get();
                currentStatus = e.status.name();
                currentRating = e.rating;
                currentEntryId = e.id;
                alreadyInWishlist = e.status == StatusEnum.WISHLIST;
                alreadyInWatched = e.status == StatusEnum.COMPLETED;
            }
        }

        List<SeasonWithEpisodesDto> rawSeasons = List.of();

        try {
            if (mediaItemDto != null && mediaItemDto.id() != null) {
                rawSeasons = tvSeasonService.getSeasonsWithEpisodes(mediaItemDto.id());
            }
        } catch (Exception ignored) {
        }

        List<EnrichedSeasonDto> seasons = buildEnrichedSeasons(rawSeasons, mediaItemDto, email);

        return new MediaDetailEnrichedResponse(
                mediaItemDto,
                castRaw,
                directorRaw,
                posterUrl,
                backdropUrl,
                imageBase,
                alreadyInWishlist,
                alreadyInWatched,
                currentStatus,
                currentRating,
                currentEntryId,
                seasons);
    }

    private List<EnrichedSeasonDto> buildEnrichedSeasons(
            List<SeasonWithEpisodesDto> rawSeasons, MediaItemDto mediaItemDto, String email) {
        if (mediaItemDto == null || mediaItemDto.id() == null) {
            return List.of();
        }
        UUID mediaItemId = mediaItemDto.id();
        Map<UUID, SeasonWatch> watchMap = Map.of();

        try {
            if (email != null && !email.isBlank()) {
                var user = authService.getUserOrNull(email);

                if (user != null) {
                    var watches = seasonWatchRepository.findByUserIdAndMediaItemId(user.id, mediaItemId);
                    watchMap = watches.stream().collect(Collectors.toMap(w -> w.season.id, w -> w, (a, b) -> a));
                }
            }
        } catch (Exception ignored) {
        }
        List<EnrichedSeasonDto> result = new ArrayList<>();

        for (SeasonWithEpisodesDto sw : rawSeasons) {
            List<EnrichedEpisodeDto> enrichedEps = new ArrayList<>();

            for (var epDto : sw.episodes()) {
                enrichedEps.add(new EnrichedEpisodeDto(
                        epDto.id(),
                        epDto.seasonNumber(),
                        epDto.episodeNumber(),
                        epDto.title(),
                        epDto.synopsis(),
                        epDto.stillPath(),
                        epDto.airDate(),
                        epDto.runtime()));
            }
            var watch = watchMap.get(sw.season().id());
            boolean watched = watch != null;
            Integer rating = watched ? watch.rating : null;
            var watchedAt = watched ? watch.watchedAt : null;
            result.add(new EnrichedSeasonDto(sw.season(), enrichedEps, watched, rating, watchedAt));
        }
        return result;
    }
}
