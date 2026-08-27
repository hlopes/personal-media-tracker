package org.hlopes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.util.Random;
import java.util.UUID;

import org.hlopes.auth.repository.UserRepository;
import org.hlopes.catalog.entity.MediaTypeEnum;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
public class TvSeasonDetailTest {

    @Inject
    UserRepository userRepository;

    @Inject
    TestDataHelper testDataHelper;

    private long nextExternalId() {
        return 200000L + Math.abs(new Random().nextInt(9000000));
    }

    private String registerAndGetJwt(String email, String password) {
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        String token = userRepository.findByEmail(email.toLowerCase()).orElseThrow().verificationToken;
        given().when().get("/api/auth/verify?token=" + token).then().statusCode(200);

        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    @Test
    public void testTvDetailRequiresAuth() {
        Long externalId = nextExternalId();

        given().redirects()
                .follow(false)
                .when()
                .get("/media/tv/" + externalId)
                .then()
                .statusCode(303)
                .header("Location", containsString("/login"));
    }

    @Test
    public void testTvDetailShowsSeasonsAndEpisodes() {
        String email = "tvdetail-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        var mediaItem = testDataHelper.createMediaItem(externalId, MediaTypeEnum.TV_SERIES, "Test Show " + externalId);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 1, "Season 1", 2);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 0, "Specials", 1);

        // first GET should contain seasons and episodes, specials at bottom
        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/media/tv/" + externalId)
                .then()
                .statusCode(200)
                .body(containsString("Season 1"))
                .body(containsString("Season 0"))
                .body(containsString("Episode 1 of Season 1"))
                .body(containsString("Episode 1 of Specials"))
                .body(containsString("/still-1-1.jpg"))
                .body(containsString("/still-0-1.jpg"))
                .body(containsString("Seasons"));

        // second GET hits cache (same content)
        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/media/tv/" + externalId)
                .then()
                .statusCode(200)
                .body(containsString("Season 1"))
                .body(containsString("Episode 2 of Season 1"));
    }

    @Test
    public void testTvDetailFallbackWhenTmdbUnavailable() {
        String email = "tvfallback-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        var mediaItem =
                testDataHelper.createMediaItem(externalId, MediaTypeEnum.TV_SERIES, "Fallback Show " + externalId);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 1, "Season 1", 1);

        // TMDB is unavailable for random externalId (test-key invalid -> fallback)
        // cached MediaItem + seasons should still render with empty cast
        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/media/tv/" + externalId)
                .then()
                .statusCode(200)
                .body(containsString("Fallback Show"))
                .body(containsString("Season 1"))
                .body(containsString("Episode 1 of Season 1"))
                .body(containsString("/still-1-1.jpg"));
    }

    @Test
    public void testMovieDetailDoesNotShowSeasons() {
        String email = "movienoseason-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        testDataHelper.createMediaItem(externalId, MediaTypeEnum.MOVIE, "Movie Plain " + externalId);

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/media/movie/" + externalId)
                .then()
                .statusCode(200)
                .body(containsString("Movie Plain"))
                .body(org.hamcrest.Matchers.not(containsString("<h2 class=\"text-sm font-semibold\">Seasons</h2>")));
    }

    @Test
    public void testSpecialsOrdering() {
        String email = "order-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        var mediaItem = testDataHelper.createMediaItem(externalId, MediaTypeEnum.TV_SERIES, "Order Show " + externalId);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 2, "Season 2", 1);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 1, "Season 1", 1);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 0, "Specials", 1);

        String html = given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/media/tv/" + externalId)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        int idx1 = html.indexOf("Season 1");
        int idx2 = html.indexOf("Season 2");
        int idx0 = html.indexOf("Specials");

        // ensure ordering: Season 1 before Season 2 before Specials
        assert idx1 >= 0 && idx2 >= 0 && idx0 >= 0;
        assert idx1 < idx2;
        assert idx2 < idx0;
    }
}
