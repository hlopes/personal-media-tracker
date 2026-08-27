package org.hlopes.library.resource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.catalog.dto.EpisodeDto;
import org.hlopes.library.dto.SeasonProgressResponse;
import org.hlopes.library.dto.SeasonWatchRequest;
import org.hlopes.library.dto.SeasonWatchResponse;
import org.hlopes.library.service.SeasonWatchService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/me/library")
@RolesAllowed("User")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SeasonWatchResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    SeasonWatchService seasonWatchService;

    @GET
    @Path("/{id}/seasons")
    public List<SeasonProgressResponse> getSeasons(@PathParam("id") UUID libraryEntryId) {
        String email = jwt.getSubject();
        return seasonWatchService.getSeasonsProgress(email, libraryEntryId);
    }

    @GET
    @Path("/{id}/seasons/{seasonNumber}/episodes")
    public List<EpisodeDto> getSeasonEpisodes(
            @PathParam("id") UUID libraryEntryId, @PathParam("seasonNumber") int seasonNumber) {
        String email = jwt.getSubject();
        return seasonWatchService.getSeasonEpisodes(email, libraryEntryId, seasonNumber);
    }

    @POST
    @Path("/{id}/seasons/{seasonNumber}/watch")
    public SeasonWatchResponse watchSeason(
            @PathParam("id") UUID libraryEntryId,
            @PathParam("seasonNumber") int seasonNumber,
            SeasonWatchRequest request) {
        String email = jwt.getSubject();
        Integer rating = request == null ? null : request.rating();
        return seasonWatchService.watchSeason(email, libraryEntryId, seasonNumber, rating);
    }

    @PATCH
    @Path("/{id}/seasons/{seasonNumber}/watch")
    public SeasonWatchResponse updateWatch(
            @PathParam("id") UUID libraryEntryId,
            @PathParam("seasonNumber") int seasonNumber,
            SeasonWatchRequest request) {
        String email = jwt.getSubject();

        if (request == null || request.rating() == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "rating must be between 1 and 5"))
                    .build());
        }
        return seasonWatchService.updateSeasonWatch(email, libraryEntryId, seasonNumber, request.rating());
    }

    @DELETE
    @Path("/{id}/seasons/{seasonNumber}/watch")
    public Response unwatch(@PathParam("id") UUID libraryEntryId, @PathParam("seasonNumber") int seasonNumber) {
        String email = jwt.getSubject();
        seasonWatchService.unwatchSeason(email, libraryEntryId, seasonNumber);
        return Response.noContent().build();
    }
}
