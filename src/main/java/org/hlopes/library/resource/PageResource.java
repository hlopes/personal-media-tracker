package org.hlopes.library.resource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.hlopes.catalog.service.TvSeasonService;
import org.hlopes.library.entity.SeasonWatch;
import org.hlopes.library.repository.SeasonWatchRepository;
import org.hlopes.library.service.LibraryService;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PageResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    AuthService authService;

    @Inject
    LibraryService libraryService;

    @Inject
    TvSeasonService tvSeasonService;

    @Inject
    SeasonWatchRepository seasonWatchRepository;

    @Inject
    @Location("library/wishlist")
    Template wishlist;

    @Inject
    @Location("library/watched")
    Template watched;

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
            String msg = URLEncoder.encode("Please login to view wishlist", StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        } catch (Exception e) {
            String msg = URLEncoder.encode("Failed to load wishlist", StandardCharsets.UTF_8);

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

            Map<UUID, Map<String, Object>> progressMap = new HashMap<>();

            try {
                for (var e : entries) {
                    var media = e.mediaItem();

                    if (media != null && "TV_SERIES".equals(media.mediaType())) {
                        List<SeasonWithEpisodesDto> rawSeasons = List.of();

                        try {
                            rawSeasons = tvSeasonService.getSeasonsWithEpisodes(media.id());
                        } catch (Exception ignored) {
                        }

                        List<EnrichedSeasonDto> seasons = buildEnrichedSeasons(rawSeasons, media, email);
                        long totalSeasons = seasons.stream()
                                .filter(s -> s.season().seasonNumber() != 0)
                                .count();
                        long watchedSeasons = seasons.stream()
                                .filter(s -> s.watched() && s.season().seasonNumber() != 0)
                                .count();

                        progressMap.put(
                                media.id(),
                                Map.of(
                                        "totalSeasons", totalSeasons,
                                        "watchedSeasons", watchedSeasons,
                                        "totalEpisodes", totalSeasons,
                                        "watchedEpisodes", watchedSeasons,
                                        "seasons", seasons));
                    }
                }
            } catch (Exception ignored) {
            }

            TemplateInstance instance = watched.data("entries", entries)
                    .data("total", total)
                    .data("currentUser", email)
                    .data("progressMap", progressMap);

            return Response.ok(instance).build();

        } catch (NotAuthorizedException e) {
            String msg = URLEncoder.encode("Please login to view watched", StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        } catch (Exception e) {
            String msg = URLEncoder.encode("Failed to load watched", StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/app?error=" + msg)).build();
        }
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
                var user = authService.getUserOrNull(email.trim().toLowerCase());

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
