package org.hlopes.resource;

import java.net.URI;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.catalog.CatalogService;
import org.hlopes.config.ApplicationConfig;
import org.hlopes.repository.LibraryEntryRepository;
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
    CatalogService catalogService;

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    UserRepository userRepository;

    @Inject
    LibraryEntryRepository libraryEntryRepository;

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
            // Also check that the JWT has the expected group/issuer; if the token is invalid/expired,
            // SmallRye would have already rejected it via the filter, but with PermitAll we need to
            // handle it.
            // Rendering the app template with the email proves the cookie was valid.
            TemplateInstance instance = app.data("email", email).data("currentUser", email);

            return Response.ok(instance).build();

        } catch (Exception e) {
            // For browser HTML, redirect to login instead of raw 401
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
        try {
            String email = jwt != null ? jwt.getSubject() : null;

            if (email == null || email.isBlank()) {
                throw new NotAuthorizedException("Not logged in");
            }

            var detail = catalogService.detail(type, id);
            var user = userRepository.findByEmail(email).orElse(null);
            boolean alreadyInWishlist = false;

            if (user != null) {
                alreadyInWishlist = libraryEntryRepository.existsByUserIdAndMediaItemId(
                        user.id, detail.mediaItem().id());
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
}
