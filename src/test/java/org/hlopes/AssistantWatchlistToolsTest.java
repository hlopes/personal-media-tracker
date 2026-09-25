package org.hlopes;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hlopes.aiinfusion.tools.AssistantSession;
import org.hlopes.aiinfusion.tools.WatchlistTools;
import org.hlopes.catalog.client.TmdbClient;
import org.hlopes.catalog.dto.TmdbMovieDetails;
import org.hlopes.catalog.dto.TmdbSearchResponse;
import org.hlopes.catalog.dto.TmdbSearchResult;
import org.hlopes.catalog.dto.TmdbTvDetails;
import org.hlopes.catalog.entity.MediaTypeEnum;
import org.hlopes.library.dto.LibraryEntryResponse;
import org.hlopes.library.service.LibraryService;
import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@QuarkusTest
public class AssistantWatchlistToolsTest {

    @Inject
    LibraryService libraryService;

    @Inject
    TestDataHelper testDataHelper;

    @Inject
    WatchlistTools watchlistTools;

    @InjectMock
    @RestClient
    TmdbClient tmdbClient;

    private long nextExternalId() {
        return 100000L + Math.abs(new Random().nextInt(9000000));
    }

    private String registerUser(String prefix) {
        String email = prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .when()
                .post("/api/helpers/auth/register")
                .then()
                .statusCode(201);

        return email;
    }

    private AssistantSession sessionFor(String email) {
        return new AssistantSession(UUID.randomUUID().toString(), email);
    }

    private void stubMovie(long externalId, String title, String releaseDate) {
        when(tmdbClient.getMovie(eq(externalId), anyString(), any()))
                .thenReturn(new TmdbMovieDetails(externalId, title, "Overview", "/p.jpg", "/b.jpg", releaseDate));
    }

    private List<LibraryEntryResponse> watchlistOf(String email) {
        return libraryService.list(email, "WATCHLIST", 0, 100);
    }

    @Test
    public void addToWatchlistCachesUnknownMovieAndCreatesWatchlistEntry() {
        String email = registerUser("assistant-add");
        long externalId = nextExternalId();
        stubMovie(externalId, "Dune: Part Two", "2024-02-27");

        String result = watchlistTools.addToWatchlist(sessionFor(email), externalId, "movie");

        assertEquals("ADDED: Dune: Part Two (2024, movie)", result);
        List<LibraryEntryResponse> watchlist = watchlistOf(email);
        assertEquals(1, watchlist.size());
        assertEquals("WATCHLIST", watchlist.get(0).status());
        assertEquals(externalId, watchlist.get(0).mediaItem().externalId());
        assertTrue(watchlist.get(0).rating() == null);
    }

    @Test
    public void addToWatchlistAddsTvSeries() {
        String email = registerUser("assistant-tv");
        long externalId = nextExternalId();
        when(tmdbClient.getTv(eq(externalId), anyString(), any()))
                .thenReturn(new TmdbTvDetails(
                        externalId, "Severance", "Overview", "/p.jpg", "/b.jpg", "2022-02-18", List.of(), List.of()));

        String result = watchlistTools.addToWatchlist(sessionFor(email), externalId, "tv");

        assertEquals("ADDED: Severance (2022, tv)", result);
        List<LibraryEntryResponse> watchlist = watchlistOf(email);
        assertEquals(1, watchlist.size());
        assertEquals("TV_SERIES", watchlist.get(0).mediaItem().mediaType());
    }

    @Test
    public void addToWatchlistReportsExistingCompletedEntryAndLeavesItUnchanged() {
        String email = registerUser("assistant-existing");
        long externalId = nextExternalId();
        testDataHelper.createMediaItem(externalId, MediaTypeEnum.MOVIE, "Arrival");
        stubMovie(externalId, "Arrival", "2016-11-10");
        libraryService.add(email, externalId, "movie", "COMPLETED", (short) 4);

        String result = watchlistTools.addToWatchlist(sessionFor(email), externalId, "movie");

        assertEquals("ALREADY_IN_LIBRARY:COMPLETED: Arrival", result);
        assertEquals(0, watchlistOf(email).size());
        List<LibraryEntryResponse> completed = libraryService.list(email, "COMPLETED", 0, 100);
        assertEquals(1, completed.size());
        assertEquals((short) 4, completed.get(0).rating());
    }

    @Test
    public void addToWatchlistWithoutAuthenticatedUserReturnsError() {
        long externalId = nextExternalId();
        stubMovie(externalId, "Anonymous Movie", "2020-01-01");

        assertTrue(watchlistTools.addToWatchlist(null, externalId, "movie").startsWith("ERROR:"));
        assertTrue(watchlistTools
                .addToWatchlist(new AssistantSession("c1", null), externalId, "movie")
                .startsWith("ERROR:"));
    }

    @Test
    public void addToWatchlistWhenCatalogUnavailableReturnsErrorAndPersistsNothing() {
        String email = registerUser("assistant-down");
        long externalId = nextExternalId();
        when(tmdbClient.getMovie(eq(externalId), anyString(), any())).thenThrow(new RuntimeException("timeout"));

        String result = watchlistTools.addToWatchlist(sessionFor(email), externalId, "movie");

        assertTrue(result.startsWith("ERROR:"), result);
        assertEquals(0, watchlistOf(email).size());
    }

    @Test
    public void addToWatchlistForUnknownExternalIdReturnsNotFound() {
        String email = registerUser("assistant-unknown");
        long externalId = nextExternalId();
        when(tmdbClient.getMovie(eq(externalId), anyString(), any()))
                .thenThrow(new WebApplicationException(Response.status(404).build()));

        assertEquals("NOT_FOUND", watchlistTools.addToWatchlist(sessionFor(email), externalId, "movie"));
        assertEquals(0, watchlistOf(email).size());
    }

    @Test
    public void addToWatchlistOnlyTouchesTheSessionUsersLibrary() {
        String alice = registerUser("assistant-alice");
        String bob = registerUser("assistant-bob");
        long externalId = nextExternalId();
        stubMovie(externalId, "Heat", "1995-12-15");

        watchlistTools.addToWatchlist(sessionFor(alice), externalId, "movie");

        assertEquals(1, watchlistOf(alice).size());
        assertEquals(0, watchlistOf(bob).size());
    }

    @Test
    public void searchCatalogListsCandidatesUsableByAddToWatchlist() {
        String query = "Dune " + UUID.randomUUID();
        when(tmdbClient.searchMulti(anyString(), eq(query), eq(1), any(), eq(false)))
                .thenReturn(new TmdbSearchResponse(
                        1,
                        List.of(
                                new TmdbSearchResult(
                                        693134L, "movie", "Dune: Part Two", null, "o", null, null, "2024-02-27", null),
                                new TmdbSearchResult(
                                        438631L, "movie", "Dune", null, "o", null, null, "2021-09-15", null),
                                new TmdbSearchResult(90228L, "tv", null, "Dune: Prophecy", "o", null, null, null, ""),
                                new TmdbSearchResult(7L, "person", null, "Denis", null, null, null, null, null)),
                        1,
                        4));

        String result = watchlistTools.searchCatalog(query, null);

        assertEquals(
                """
                externalId=693134 | mediaType=movie | title=Dune: Part Two | year=2024
                externalId=438631 | mediaType=movie | title=Dune | year=2021
                externalId=90228 | mediaType=tv | title=Dune: Prophecy | year=unknown""",
                result);
    }

    @Test
    public void searchCatalogWithNoMatchesReturnsNotFound() {
        String query = "Nothing " + UUID.randomUUID();
        when(tmdbClient.searchMovie(anyString(), eq(query), eq(1), any(), eq(false)))
                .thenReturn(new TmdbSearchResponse(1, List.of(), 0, 0));

        assertEquals("NOT_FOUND", watchlistTools.searchCatalog(query, "movie"));
    }
}
