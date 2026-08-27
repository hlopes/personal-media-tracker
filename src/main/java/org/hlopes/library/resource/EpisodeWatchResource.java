package org.hlopes.library.resource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.library.dto.EpisodeWatchRequest;
import org.hlopes.library.dto.EpisodeWatchResponse;
import org.hlopes.library.dto.EpisodeWithWatchResponse;
import org.hlopes.library.dto.SeasonProgressResponse;
import org.hlopes.library.service.EpisodeWatchService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/me")
@RolesAllowed("User")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EpisodeWatchResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    EpisodeWatchService episodeWatchService;

    @GET
    @Path("/library/{id}/seasons")
    public List<SeasonProgressResponse> getSeasons(@PathParam("id") UUID libraryEntryId) {
        String email = jwt.getSubject();

        return episodeWatchService.getSeasonsProgress(email, libraryEntryId);
    }

    @GET
    @Path("/library/{id}/seasons/{seasonNumber}/episodes")
    public List<EpisodeWithWatchResponse> getSeasonEpisodes(
            @PathParam("id") UUID libraryEntryId, @PathParam("seasonNumber") int seasonNumber) {
        String email = jwt.getSubject();

        return episodeWatchService.getEpisodesWithWatch(email, libraryEntryId, seasonNumber);
    }

    @POST
    @Path("/library/{id}/episodes/{episodeId}/watch")
    public EpisodeWatchResponse watchEpisode(
            @PathParam("id") UUID libraryEntryId, @PathParam("episodeId") UUID episodeId, EpisodeWatchRequest request) {
        String email = jwt.getSubject();
        Integer rating = request == null ? null : request.rating();

        return episodeWatchService.watchEpisode(email, libraryEntryId, episodeId, rating);
    }

    @PATCH
    @Path("/library/{id}/episodes/{episodeId}/watch")
    public EpisodeWatchResponse updateWatch(
            @PathParam("id") UUID libraryEntryId, @PathParam("episodeId") UUID episodeId, EpisodeWatchRequest request) {
        String email = jwt.getSubject();

        if (request == null || request.rating() == null) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "rating must be between 1 and 5"))
                    .build());
        }

        return episodeWatchService.updateEpisodeWatch(email, libraryEntryId, episodeId, request.rating());
    }

    @DELETE
    @Path("/library/{id}/episodes/{episodeId}/watch")
    public Response unwatch(@PathParam("id") UUID libraryEntryId, @PathParam("episodeId") UUID episodeId) {
        String email = jwt.getSubject();
        episodeWatchService.unwatchEpisode(email, libraryEntryId, episodeId);

        return Response.noContent().build();
    }

    @POST
    @Path("/library/{id}/seasons/{seasonNumber}/watch")
    public List<EpisodeWatchResponse> watchSeason(
            @PathParam("id") UUID libraryEntryId,
            @PathParam("seasonNumber") int seasonNumber,
            EpisodeWatchRequest request) {
        String email = jwt.getSubject();
        Integer rating = request == null ? null : request.rating();

        return episodeWatchService.watchSeason(email, libraryEntryId, seasonNumber, rating);
    }
}
