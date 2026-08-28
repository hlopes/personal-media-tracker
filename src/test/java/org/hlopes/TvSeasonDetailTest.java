package org.hlopes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

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
                .post("/api/helpers/auth/register")
                .then()
                .statusCode(201);

        String token = userRepository.findByEmail(email.toLowerCase()).orElseThrow().verificationToken;
        given().when().get("/api/helpers/auth/verify?token=" + token).then().statusCode(200);

        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/helpers/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    @Test
    public void testTvDetailRequiresAuth() {
        Long externalId = nextExternalId();

        given().when().get("/api/media/tv/" + externalId).then().statusCode(401);
    }

    @Test
    public void testTvDetailShowsSeasonsAndEpisodes() {
        String email = "tvdetail-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        var mediaItem = testDataHelper.createMediaItem(externalId, MediaTypeEnum.TV_SERIES, "Test Show " + externalId);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 1, "Season 1", 2);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 0, "Specials", 1);

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/media/tv/" + externalId)
                .then()
                .statusCode(200)
                .body("mediaItem.title", containsString("Test Show"))
                .body("seasons.size()", is(2))
                .body("seasons[0].season.name", is("Season 1"))
                .body("seasons[0].season.seasonNumber", is(1))
                .body("seasons[0].episodes.size()", is(2))
                .body("seasons[0].episodes[0].title", is("Episode 1 of Season 1"))
                .body("seasons[0].episodes[0].stillPath", is("/still-1-1.jpg"))
                .body("seasons[1].season.name", is("Specials"))
                .body("seasons[1].episodes[0].title", is("Episode 1 of Specials"))
                .body("seasons[1].episodes[0].stillPath", is("/still-0-1.jpg"));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/media/tv/" + externalId)
                .then()
                .statusCode(200)
                .body("seasons[0].episodes[1].title", is("Episode 2 of Season 1"));
    }

    @Test
    public void testTvDetailFallbackWhenTmdbUnavailable() {
        String email = "tvfallback-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        var mediaItem =
                testDataHelper.createMediaItem(externalId, MediaTypeEnum.TV_SERIES, "Fallback Show " + externalId);
        testDataHelper.createTvSeasonWithEpisodes(mediaItem, 1, "Season 1", 1);

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/media/tv/" + externalId)
                .then()
                .statusCode(200)
                .body("mediaItem.title", containsString("Fallback Show"))
                .body("seasons[0].season.name", is("Season 1"))
                .body("seasons[0].episodes[0].title", is("Episode 1 of Season 1"))
                .body("seasons[0].episodes[0].stillPath", is("/still-1-1.jpg"));
    }

    @Test
    public void testMovieDetailDoesNotShowSeasons() {
        String email = "movienoseason-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        testDataHelper.createMediaItem(externalId, MediaTypeEnum.MOVIE, "Movie Plain " + externalId);

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/media/movie/" + externalId)
                .then()
                .statusCode(200)
                .body("mediaItem.title", containsString("Movie Plain"))
                .body("seasons.size()", is(0));
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

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/media/tv/" + externalId)
                .then()
                .statusCode(200)
                .body("seasons.size()", is(3))
                .body("seasons[0].season.name", is("Season 1"))
                .body("seasons[1].season.name", is("Season 2"))
                .body("seasons[2].season.name", is("Specials"));
    }
}
