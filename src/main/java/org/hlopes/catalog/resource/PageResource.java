package org.hlopes.catalog.resource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.auth.service.AuthService;
import org.hlopes.catalog.dto.EnrichedEpisodeDto;
import org.hlopes.catalog.dto.EnrichedSeasonDto;
import org.hlopes.catalog.dto.MediaItemDto;
import org.hlopes.catalog.dto.SeasonWithEpisodesDto;
import org.hlopes.catalog.entity.MediaTypeEnum;
import org.hlopes.catalog.repository.MediaItemRepository;
import org.hlopes.catalog.service.CatalogService;
import org.hlopes.catalog.service.TvSeasonService;
import org.hlopes.config.ApplicationConfig;
import org.hlopes.library.entity.EpisodeWatch;
import org.hlopes.library.entity.StatusEnum;
import org.hlopes.library.repository.EpisodeWatchRepository;
import org.hlopes.library.repository.LibraryEntryRepository;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PageResource {

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
    EpisodeWatchRepository episodeWatchRepository;

    @Inject
    @Location("catalog/detail")
    Template catalog_detail;

    @GET
    @Path("media/{type}/{id}")
    @PermitAll
    public Response getMediaDetail(@PathParam("type") String type, @PathParam("id") Long id) {
        String email = null;

        try {
            email = jwt != null ? jwt.getSubject() : null;

            if (email == null || email.isBlank()) {
                throw new NotAuthorizedException("Not logged in");
            }

            var detail = catalogService.detail(type, id);
            var user = authService.getUserOrNull(email);
            boolean alreadyInWishlist = false;
            boolean alreadyInWatched = false;
            String currentStatus = null;
            Integer currentRating = null;
            UUID currentEntryId = null;

            if (user != null) {
                var opt = libraryEntryRepository.findByUserIdAndMediaItemId(
                        user.id, detail.mediaItem().id());

                if (opt.isPresent()) {
                    var e = opt.get();
                    currentStatus = e.status.name();
                    currentRating = e.rating;
                    currentEntryId = e.id;
                    alreadyInWishlist = e.status == StatusEnum.WISHLIST;
                    alreadyInWatched = e.status == StatusEnum.COMPLETED;
                }
            }

            String imageBase = applicationConfig.tmdb().imageBaseUrl();
            String posterUrl = detail.mediaItem().posterPath() != null
                    ? imageBase + "/w500" + detail.mediaItem().posterPath()
                    : null;
            String backdropUrl = detail.mediaItem().backdropPath() != null
                    ? imageBase + "/w1280" + detail.mediaItem().backdropPath()
                    : null;

            List<SeasonWithEpisodesDto> rawSeasons = List.of();

            try {
                if (detail.mediaItem() != null && detail.mediaItem().id() != null) {
                    rawSeasons = tvSeasonService.getSeasonsWithEpisodes(
                            detail.mediaItem().id());
                }
            } catch (Exception ignored) {
            }

            List<EnrichedSeasonDto> seasons = buildEnrichedSeasons(rawSeasons, detail.mediaItem(), email);

            TemplateInstance instance = catalog_detail
                    .data("mediaItem", detail.mediaItem())
                    .data("cast", detail.credits().cast())
                    .data("director", detail.credits().director())
                    .data("posterUrl", posterUrl)
                    .data("backdropUrl", backdropUrl)
                    .data("alreadyInWishlist", alreadyInWishlist)
                    .data("alreadyInWatched", alreadyInWatched)
                    .data("currentStatus", currentStatus)
                    .data("currentRating", currentRating)
                    .data("currentEntryId", currentEntryId)
                    .data("currentUser", email)
                    .data("seasons", seasons)
                    .data("imageBase", imageBase);

            return Response.ok(instance).build();

        } catch (NotAuthorizedException e) {
            String msg = URLEncoder.encode("Please login to view media", StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        } catch (NotFoundException e) {
            throw e;
        } catch (WebApplicationException e) {
            // Fallback to cached MediaItem when TMDB is unavailable (including 404 if cached exists)

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

                if (mt != null && email != null) {
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

                        TemplateInstance instance = catalog_detail
                                .data("mediaItem", mediaItemDto)
                                .data("cast", List.of())
                                .data("director", null)
                                .data("posterUrl", posterUrl)
                                .data("backdropUrl", backdropUrl)
                                .data("alreadyInWishlist", alreadyInWishlist)
                                .data("alreadyInWatched", alreadyInWatched)
                                .data("currentStatus", currentStatus)
                                .data("currentRating", currentRating)
                                .data("currentEntryId", currentEntryId)
                                .data("currentUser", email)
                                .data("seasons", seasons)
                                .data("imageBase", imageBase);

                        return Response.ok(instance).build();
                    }
                }
            } catch (Exception ignored) {
            }

            if (e.getResponse().getStatus() == 404) {
                throw new NotFoundException("Media not found");
            }

            throw e;
        } catch (Exception e) {
            String msg = URLEncoder.encode("Media not found", StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/app?error=" + msg)).build();
        }
    }

    private List<EnrichedSeasonDto> buildEnrichedSeasons(
            List<SeasonWithEpisodesDto> rawSeasons, MediaItemDto mediaItemDto, String email) {
        if (mediaItemDto == null || mediaItemDto.id() == null) {
            return List.of();
        }
        // find mediaItem entity id is mediaItemDto.id()
        UUID mediaItemId = mediaItemDto.id();
        Map<UUID, EpisodeWatch> watchMap = Map.of();

        try {
            if (email != null && !email.isBlank()) {
                var user = authService.getUserOrNull(email);

                if (user != null) {
                    var watches = episodeWatchRepository.findByUserIdAndMediaItemId(user.id, mediaItemId);
                    watchMap = watches.stream().collect(Collectors.toMap(w -> w.episode.id, w -> w, (a, b) -> a));
                }
            }
        } catch (Exception ignored) {
        }
        List<EnrichedSeasonDto> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (SeasonWithEpisodesDto sw : rawSeasons) {
            List<EnrichedEpisodeDto> enrichedEps = new ArrayList<>();

            for (var epDto : sw.episodes()) {
                var watch = watchMap.get(epDto.id());
                boolean watched = watch != null;
                Integer rating = watched ? watch.rating : null;
                boolean future = epDto.airDate() != null && epDto.airDate().isAfter(today);
                enrichedEps.add(new EnrichedEpisodeDto(
                        epDto.id(),
                        epDto.seasonNumber(),
                        epDto.episodeNumber(),
                        epDto.title(),
                        epDto.synopsis(),
                        epDto.stillPath(),
                        epDto.airDate(),
                        epDto.runtime(),
                        watched,
                        rating,
                        future));
            }
            long watchedCount =
                    enrichedEps.stream().filter(EnrichedEpisodeDto::watched).count();
            result.add(new EnrichedSeasonDto(sw.season(), enrichedEps, watchedCount));
        }
        return result;
    }
}
