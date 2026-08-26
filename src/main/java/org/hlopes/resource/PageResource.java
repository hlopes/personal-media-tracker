package org.hlopes.resource;

import java.net.URI;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.catalog.CatalogService;
import org.hlopes.catalog.TvSeasonService;
import org.hlopes.catalog.dto.EnrichedEpisodeDto;
import org.hlopes.catalog.dto.EnrichedSeasonDto;
import org.hlopes.catalog.dto.SeasonWithEpisodesDto;
import org.hlopes.config.ApplicationConfig;
import org.hlopes.repository.EpisodeWatchRepository;
import org.hlopes.repository.LibraryEntryRepository;
import org.hlopes.repository.MediaItemRepository;
import org.hlopes.repository.UserRepository;
import org.hlopes.service.LibraryService;

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
    Template index;

    @Inject
    Template app;

    @Inject
    @Location("auth/login")
    Template auth_login;

    @Inject
    @Location("auth/register")
    Template auth_register;

    @Inject
    @Location("auth/verification-sent")
    Template auth_verification_sent;

    @Inject
    @Location("catalog/detail")
    Template catalog_detail;

    @Inject
    @Location("wishlist")
    Template wishlist;

    @Inject
    @Location("watched")
    Template watched;

    @Inject
    CatalogService catalogService;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    UserRepository userRepository;

    @Inject
    LibraryEntryRepository libraryEntryRepository;

    @Inject
    MediaItemRepository mediaItemRepository;

    @Inject
    LibraryService libraryService;

    @Inject
    TvSeasonService tvSeasonService;

    @Inject
    EpisodeWatchRepository episodeWatchRepository;

    @Inject
    JsonWebToken jwt;

    @GET
    @PermitAll
    public TemplateInstance getIndex() {
        String email = null;

        try {
            if (jwt != null && jwt.getSubject() != null) {
                email = jwt.getSubject();
            }

        } catch (Exception ignored) {
        }

        return index.data("currentUser", email);
    }

    @GET
    @Path("app")
    @PermitAll
    public Response getApp() {
        try {
            String email = jwt != null ? jwt.getSubject() : null;

            if (email == null || email.isBlank()) {
                throw new NotAuthorizedException("Not logged in");
            }

            TemplateInstance instance = app.data("email", email).data("currentUser", email);

            return Response.ok(instance).build();

        } catch (Exception e) {
            String msg = java.net.URLEncoder.encode(
                    "Please login to access your library", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        }
    }

    @GET
    @Path("login")
    @PermitAll
    public TemplateInstance getLogin(
            @QueryParam("error") String error,
            @QueryParam("message") String message,
            @QueryParam("email") String email) {
        return auth_login
                .data("error", error)
                .data("message", message)
                .data("email", email)
                .data("currentUser", null);
    }

    @GET
    @Path("register")
    @PermitAll
    public TemplateInstance getRegister(@QueryParam("error") String error, @QueryParam("email") String email) {
        return auth_register.data("error", error).data("email", email).data("currentUser", null);
    }

    @GET
    @Path("verification-sent")
    @PermitAll
    public TemplateInstance getVerificationSent(
            @QueryParam("email") String email,
            @QueryParam("message") String message,
            @QueryParam("error") String error) {
        return auth_verification_sent
                .data("email", email)
                .data("resendMessage", message)
                .data("resendError", error)
                .data("currentUser", null);
    }

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
            var user = userRepository.findByEmail(email).orElse(null);
            boolean alreadyInWishlist = false;
            boolean alreadyInWatched = false;
            String currentStatus = null;
            Integer currentRating = null;
            java.util.UUID currentEntryId = null;

            if (user != null) {
                var opt = libraryEntryRepository.findByUserIdAndMediaItemId(
                        user.id, detail.mediaItem().id());

                if (opt.isPresent()) {
                    var e = opt.get();
                    currentStatus = e.status.name();
                    currentRating = e.rating;
                    currentEntryId = e.id;
                    alreadyInWishlist = e.status == org.hlopes.entity.Status.WISHLIST;
                    alreadyInWatched = e.status == org.hlopes.entity.Status.COMPLETED;
                }
            }

            String imageBase = applicationConfig.tmdb().imageBaseUrl();
            String posterUrl = detail.mediaItem().posterPath() != null
                    ? imageBase + "/w500" + detail.mediaItem().posterPath()
                    : null;
            String backdropUrl = detail.mediaItem().backdropPath() != null
                    ? imageBase + "/w1280" + detail.mediaItem().backdropPath()
                    : null;

            java.util.List<org.hlopes.catalog.dto.SeasonWithEpisodesDto> rawSeasons = java.util.List.of();

            try {
                if (detail.mediaItem() != null && detail.mediaItem().id() != null) {
                    rawSeasons = tvSeasonService.getSeasonsWithEpisodes(
                            detail.mediaItem().id());
                }
            } catch (Exception ignored) {
            }

            java.util.List<EnrichedSeasonDto> seasons = buildEnrichedSeasons(rawSeasons, detail.mediaItem(), email);

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
            String msg =
                    java.net.URLEncoder.encode("Please login to view media", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        } catch (NotFoundException e) {
            throw e;
        } catch (jakarta.ws.rs.WebApplicationException e) {
            // Fallback to cached MediaItem when TMDB is unavailable (including 404 if cached exists)

            try {
                String normalized = type == null ? "" : type.trim().toLowerCase();
                org.hlopes.entity.MediaType mt = null;

                if (normalized.equals("movie")) {
                    mt = org.hlopes.entity.MediaType.MOVIE;
                } else if (normalized.equals("tv")
                        || normalized.equals("tv_series")
                        || normalized.equals("tv-series")) {
                    mt = org.hlopes.entity.MediaType.TV_SERIES;
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

                        var mediaItemDto = new org.hlopes.catalog.dto.MediaItemDto(
                                mediaItem.id,
                                mediaItem.externalId,
                                mediaItem.mediaType.name(),
                                mediaItem.title,
                                mediaItem.synopsis,
                                mediaItem.posterPath,
                                mediaItem.backdropPath,
                                mediaItem.releaseDate);

                        var user = userRepository.findByEmail(email).orElse(null);
                        boolean alreadyInWishlist = false;
                        boolean alreadyInWatched = false;
                        String currentStatus = null;
                        Integer currentRating = null;
                        java.util.UUID currentEntryId = null;

                        if (user != null) {
                            var opt = libraryEntryRepository.findByUserIdAndMediaItemId(user.id, mediaItem.id);

                            if (opt.isPresent()) {
                                var en = opt.get();
                                currentStatus = en.status.name();
                                currentRating = en.rating;
                                currentEntryId = en.id;
                                alreadyInWishlist = en.status == org.hlopes.entity.Status.WISHLIST;
                                alreadyInWatched = en.status == org.hlopes.entity.Status.COMPLETED;
                            }
                        }

                        java.util.List<org.hlopes.catalog.dto.SeasonWithEpisodesDto> rawSeasons = java.util.List.of();

                        try {
                            rawSeasons = tvSeasonService.getSeasonsWithEpisodes(mediaItem.id);
                        } catch (Exception ignored) {
                        }

                        java.util.List<EnrichedSeasonDto> seasons =
                                buildEnrichedSeasons(rawSeasons, mediaItemDto, email);

                        TemplateInstance instance = catalog_detail
                                .data("mediaItem", mediaItemDto)
                                .data("cast", java.util.List.of())
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
            String msg = java.net.URLEncoder.encode("Media not found", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/app?error=" + msg)).build();
        }
    }

    private java.util.List<EnrichedSeasonDto> buildEnrichedSeasons(
            java.util.List<SeasonWithEpisodesDto> rawSeasons,
            org.hlopes.catalog.dto.MediaItemDto mediaItemDto,
            String email) {
        if (mediaItemDto == null || mediaItemDto.id() == null) {
            return java.util.List.of();
        }
        // find mediaItem entity id is mediaItemDto.id()
        java.util.UUID mediaItemId = mediaItemDto.id();
        java.util.Map<java.util.UUID, org.hlopes.entity.EpisodeWatch> watchMap = java.util.Map.of();

        try {
            if (email != null && !email.isBlank()) {
                var userOpt = userRepository.findByEmail(email.trim().toLowerCase());

                if (userOpt.isPresent()) {
                    var watches = episodeWatchRepository.findByUserIdAndMediaItemId(userOpt.get().id, mediaItemId);
                    watchMap = watches.stream()
                            .collect(java.util.stream.Collectors.toMap(w -> w.episode.id, w -> w, (a, b) -> a));
                }
            }
        } catch (Exception ignored) {
        }
        java.util.List<EnrichedSeasonDto> result = new java.util.ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();

        for (SeasonWithEpisodesDto sw : rawSeasons) {
            java.util.List<EnrichedEpisodeDto> enrichedEps = new java.util.ArrayList<>();

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

    private java.util.List<EnrichedSeasonDto> buildEnrichedSeasons(
            java.util.List<SeasonWithEpisodesDto> rawSeasons, org.hlopes.entity.MediaItem mediaItem, String email) {
        if (mediaItem == null || mediaItem.id == null) {
            return java.util.List.of();
        }
        org.hlopes.catalog.dto.MediaItemDto dto = new org.hlopes.catalog.dto.MediaItemDto(
                mediaItem.id,
                mediaItem.externalId,
                mediaItem.mediaType.name(),
                mediaItem.title,
                mediaItem.synopsis,
                mediaItem.posterPath,
                mediaItem.backdropPath,
                mediaItem.releaseDate);

        return buildEnrichedSeasons(rawSeasons, dto, email);
    }

    @GET
    @Path("wishlist")
    @PermitAll
    public Response getWishlist(
            @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("20") int size) {
        try {
            String email = jwt != null ? jwt.getSubject() : null;

            if (email == null || email.isBlank()) {
                throw new NotAuthorizedException("Not logged in");
            }

            var entries = libraryService.list(email, "WISHLIST", page, size);
            long total = libraryService.count(email, "WISHLIST");

            TemplateInstance instance =
                    wishlist.data("entries", entries).data("total", total).data("currentUser", email);

            return Response.ok(instance).build();

        } catch (NotAuthorizedException e) {
            String msg = java.net.URLEncoder.encode(
                    "Please login to view wishlist", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        } catch (Exception e) {
            String msg = java.net.URLEncoder.encode("Failed to load wishlist", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/app?error=" + msg)).build();
        }
    }

    @GET
    @Path("watched")
    @PermitAll
    public Response getWatched(
            @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("20") int size) {
        try {
            String email = jwt != null ? jwt.getSubject() : null;

            if (email == null || email.isBlank()) {
                throw new NotAuthorizedException("Not logged in");
            }

            var entries = libraryService.list(email, "COMPLETED", page, size);
            long total = libraryService.count(email, "COMPLETED");

            TemplateInstance instance =
                    watched.data("entries", entries).data("total", total).data("currentUser", email);

            return Response.ok(instance).build();

        } catch (NotAuthorizedException e) {
            String msg =
                    java.net.URLEncoder.encode("Please login to view watched", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        } catch (Exception e) {
            String msg = java.net.URLEncoder.encode("Failed to load watched", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/app?error=" + msg)).build();
        }
    }
}
