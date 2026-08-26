package org.hlopes.resource;

import java.net.URI;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.catalog.CatalogService;
import org.hlopes.config.ApplicationConfig;
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
                    .data("currentUser", email);

            return Response.ok(instance).build();

        } catch (NotAuthorizedException e) {
            String msg =
                    java.net.URLEncoder.encode("Please login to view media", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        } catch (NotFoundException e) {
            throw e;
        } catch (jakarta.ws.rs.WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                throw new NotFoundException("Media not found");
            }

            // Fallback to cached MediaItem when TMDB is unavailable

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
                                .data("currentUser", email);

                        return Response.ok(instance).build();
                    }
                }
            } catch (Exception ignored) {
            }

            throw e;
        } catch (Exception e) {
            String msg = java.net.URLEncoder.encode("Media not found", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/app?error=" + msg)).build();
        }
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
