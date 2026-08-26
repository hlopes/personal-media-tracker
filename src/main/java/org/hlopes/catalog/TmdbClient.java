package org.hlopes.catalog;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.hlopes.catalog.dto.TmdbCredits;
import org.hlopes.catalog.dto.TmdbMovieDetails;
import org.hlopes.catalog.dto.TmdbSearchResponse;
import org.hlopes.catalog.dto.TmdbSeasonDetails;
import org.hlopes.catalog.dto.TmdbTvDetails;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@RegisterRestClient(configKey = "tmdb")
public interface TmdbClient {

    @GET
    @Path("/search/multi")
    TmdbSearchResponse searchMulti(
            @QueryParam("api_key") String apiKey,
            @QueryParam("query") String query,
            @QueryParam("page") int page,
            @QueryParam("language") String language,
            @QueryParam("include_adult") boolean includeAdult);

    @GET
    @Path("/search/movie")
    TmdbSearchResponse searchMovie(
            @QueryParam("api_key") String apiKey,
            @QueryParam("query") String query,
            @QueryParam("page") int page,
            @QueryParam("language") String language,
            @QueryParam("include_adult") boolean includeAdult);

    @GET
    @Path("/search/tv")
    TmdbSearchResponse searchTv(
            @QueryParam("api_key") String apiKey,
            @QueryParam("query") String query,
            @QueryParam("page") int page,
            @QueryParam("language") String language,
            @QueryParam("include_adult") boolean includeAdult);

    @GET
    @Path("/movie/{id}")
    TmdbMovieDetails getMovie(
            @PathParam("id") Long id, @QueryParam("api_key") String apiKey, @QueryParam("language") String language);

    @GET
    @Path("/tv/{id}")
    TmdbTvDetails getTv(
            @PathParam("id") Long id, @QueryParam("api_key") String apiKey, @QueryParam("language") String language);

    @GET
    @Path("/movie/{id}/credits")
    TmdbCredits getMovieCredits(
            @PathParam("id") Long id, @QueryParam("api_key") String apiKey, @QueryParam("language") String language);

    @GET
    @Path("/tv/{id}/credits")
    TmdbCredits getTvCredits(
            @PathParam("id") Long id, @QueryParam("api_key") String apiKey, @QueryParam("language") String language);

    @GET
    @Path("/tv/{id}/season/{seasonNumber}")
    TmdbSeasonDetails getSeason(
            @PathParam("id") Long id,
            @PathParam("seasonNumber") int seasonNumber,
            @QueryParam("api_key") String apiKey,
            @QueryParam("language") String language);
}
