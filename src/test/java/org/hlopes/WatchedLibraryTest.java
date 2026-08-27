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
public class WatchedLibraryTest {

    @Inject
    UserRepository userRepository;

    @Inject
    TestDataHelper testDataHelper;

    private long nextExternalId() {
        return 100000L + Math.abs(new Random().nextInt(9000000));
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
    public void testWatchedRequiresAuth() {
        given().when().get("/api/me/library?status=COMPLETED").then().statusCode(401);
        given().contentType(ContentType.JSON)
                .body("{\"externalId\":1,\"mediaType\":\"movie\",\"status\":\"COMPLETED\",\"rating\":5}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(401);
        given().when().delete("/api/me/library/" + UUID.randomUUID()).then().statusCode(401);
        given().contentType(ContentType.JSON)
                .body("{\"rating\":4}")
                .when()
                .patch("/api/me/library/" + UUID.randomUUID())
                .then()
                .statusCode(401);
        given().redirects()
                .follow(false)
                .when()
                .get("/watched")
                .then()
                .statusCode(303)
                .header("Location", containsString("/login"));
        given().redirects()
                .follow(false)
                .when()
                .get("/media/movie/603")
                .then()
                .statusCode(303)
                .header("Location", containsString("/login"));
    }

    @Test
    public void testAddWatchedWithRating() {
        String email = "watched-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        testDataHelper.createMediaItem(externalId, MediaTypeEnum.MOVIE, "Watched Movie " + externalId);

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + externalId
                        + ",\"mediaType\":\"movie\",\"status\":\"COMPLETED\",\"rating\":5}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(201)
                .body("status", is("COMPLETED"))
                .body("rating", is(5))
                .body("mediaItem.externalId", is(externalId.intValue()))
                .body("mediaItem.mediaType", is("MOVIE"));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=COMPLETED")
                .then()
                .statusCode(200)
                .body("entries.size()", greaterThanOrEqualTo(1))
                .body("entries[0].rating", is(5))
                .body("total", greaterThanOrEqualTo(1));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=WISHLIST")
                .then()
                .statusCode(200)
                .body("entries.size()", is(0));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/watched")
                .then()
                .statusCode(200)
                .body(containsString("Watched"))
                .body(containsString("★★★★★"))
                .body(containsString("(5/5)"))
                .body(containsString("/media/movie/" + externalId));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/wishlist")
                .then()
                .statusCode(200)
                .body(not(containsString("★★★★★")));
    }

    @Test
    public void testAddWatchedValidation() {
        String email = "watched-val-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        testDataHelper.createMediaItem(externalId, MediaTypeEnum.MOVIE);

        Long ext2 = nextExternalId();
        testDataHelper.createMediaItem(ext2, MediaTypeEnum.MOVIE);
        Long ext3 = nextExternalId();
        testDataHelper.createMediaItem(ext3, MediaTypeEnum.TV_SERIES);
        Long ext4 = nextExternalId();
        testDataHelper.createMediaItem(ext4, MediaTypeEnum.MOVIE);
        Long ext5 = nextExternalId();
        testDataHelper.createMediaItem(ext5, MediaTypeEnum.MOVIE);

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + externalId + ",\"mediaType\":\"movie\",\"status\":\"COMPLETED\"}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(400)
                .body("error", containsString("rating required"));

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + ext2 + ",\"mediaType\":\"movie\",\"status\":\"COMPLETED\",\"rating\":0}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(400)
                .body("error", containsString("between 1 and 5"));

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + ext3 + ",\"mediaType\":\"tv\",\"status\":\"COMPLETED\",\"rating\":6}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(400);

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + ext4 + ",\"mediaType\":\"movie\",\"status\":\"WISHLIST\",\"rating\":3}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(400)
                .body("error", containsString("not allowed"));

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + ext5 + ",\"mediaType\":\"movie\",\"status\":\"COMPLETED\",\"rating\":3}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(201);

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + ext5 + ",\"mediaType\":\"movie\",\"status\":\"WISHLIST\",\"rating\":null}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(409);
    }

    @Test
    public void testPatchUpdateRating() {
        String email = "patch-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        testDataHelper.createMediaItem(externalId, MediaTypeEnum.MOVIE);

        String entryId = given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + externalId
                        + ",\"mediaType\":\"movie\",\"status\":\"COMPLETED\",\"rating\":3}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"rating\":5}")
                .when()
                .patch("/api/me/library/" + entryId)
                .then()
                .statusCode(200)
                .body("rating", is(5))
                .body("status", is("COMPLETED"));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=COMPLETED")
                .then()
                .statusCode(200)
                .body("entries[0].rating", is(5));

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"rating\":0}")
                .when()
                .patch("/api/me/library/" + entryId)
                .then()
                .statusCode(400);

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .patch("/api/me/library/" + entryId)
                .then()
                .statusCode(400);
    }

    @Test
    public void testTransitionWishlistToWatched() {
        String email = "trans-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        testDataHelper.createMediaItem(externalId, MediaTypeEnum.MOVIE, "Transition Movie");

        String entryId = given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + externalId + ",\"mediaType\":\"movie\"}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(201)
                .body("status", is("WISHLIST"))
                .body("rating", nullValue())
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"status\":\"COMPLETED\",\"rating\":4}")
                .when()
                .patch("/api/me/library/" + entryId)
                .then()
                .statusCode(200)
                .body("status", is("COMPLETED"))
                .body("rating", is(4));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=WISHLIST")
                .then()
                .statusCode(200)
                .body("entries.size()", is(0));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=COMPLETED")
                .then()
                .statusCode(200)
                .body("entries.size()", is(1))
                .body("entries[0].rating", is(4));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/watched")
                .then()
                .statusCode(200)
                .body(containsString("★★★★☆"))
                .body(containsString("Transition Movie"));

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"status\":\"WISHLIST\"}")
                .when()
                .patch("/api/me/library/" + entryId)
                .then()
                .statusCode(200)
                .body("status", is("WISHLIST"))
                .body("rating", nullValue());

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=WISHLIST")
                .then()
                .statusCode(200)
                .body("entries.size()", is(1));
    }

    @Test
    public void testRemoveFromWatched() {
        String email = "remove-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");
        Long externalId = nextExternalId();
        testDataHelper.createMediaItem(externalId, MediaTypeEnum.MOVIE);

        String entryId = given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + externalId
                        + ",\"mediaType\":\"movie\",\"status\":\"COMPLETED\",\"rating\":2}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/watched")
                .then()
                .statusCode(200)
                .body(containsString("★★☆☆☆"));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .delete("/api/me/library/" + entryId)
                .then()
                .statusCode(204);

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=COMPLETED")
                .then()
                .statusCode(200)
                .body("entries.size()", is(0));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .delete("/api/me/library/" + entryId)
                .then()
                .statusCode(404);

        Long externalId2 = nextExternalId();
        testDataHelper.createMediaItem(externalId2, MediaTypeEnum.MOVIE);
        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + externalId2
                        + ",\"mediaType\":\"movie\",\"status\":\"COMPLETED\",\"rating\":5}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(201);

        String email2 = "remove2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt2 = registerAndGetJwt(email2, "password123");
        given().header("Authorization", "Bearer " + jwt2)
                .when()
                .get("/api/me/library?status=COMPLETED")
                .then()
                .statusCode(200)
                .body("entries.size()", is(0));

        given().header("Authorization", "Bearer " + jwt2)
                .when()
                .delete("/api/me/library/" + entryId)
                .then()
                .statusCode(404);
    }

    @Test
    public void testWatchedIsolationAndPagination() {
        String email = "iso-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");

        for (int i = 0; i < 3; i++) {
            Long ext = nextExternalId();
            testDataHelper.createMediaItem(ext, MediaTypeEnum.MOVIE, "Iso Movie " + i + " " + ext);
            given().header("Authorization", "Bearer " + jwt)
                    .contentType(ContentType.JSON)
                    .body("{\"externalId\":" + ext + ",\"mediaType\":\"movie\",\"status\":\"COMPLETED\",\"rating\":"
                            + (i + 3) + "}")
                    .when()
                    .post("/api/me/library")
                    .then()
                    .statusCode(201);
        }

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=COMPLETED&page=0&size=2")
                .then()
                .statusCode(200)
                .body("entries.size()", is(2))
                .body("total", is(3))
                .body("page", is(0))
                .body("size", is(2));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me/library?status=COMPLETED&page=1&size=2")
                .then()
                .statusCode(200)
                .body("entries.size()", is(1));
    }

    @Test
    public void testDetailPageShowsWatchedAndWishlistStates() {
        String email = "detail-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");

        Long extWishlist = nextExternalId();
        testDataHelper.createMediaItem(extWishlist, MediaTypeEnum.MOVIE, "Detail Wishlist Movie");
        Long extWatched = nextExternalId();
        testDataHelper.createMediaItem(extWatched, MediaTypeEnum.TV_SERIES, "Detail Watched Show");

        Long extNone = nextExternalId();
        testDataHelper.createMediaItem(extNone, MediaTypeEnum.MOVIE, "Detail None Movie");

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + extWishlist + ",\"mediaType\":\"movie\"}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(201);

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.JSON)
                .body("{\"externalId\":" + extWatched + ",\"mediaType\":\"tv\",\"status\":\"COMPLETED\",\"rating\":4}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(201);

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/media/movie/" + extWishlist)
                .then()
                .statusCode(200)
                .body(containsString("Already in Wishlist"))
                .body(containsString("Mark as Watched"));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/media/tv/" + extWatched)
                .then()
                .statusCode(200)
                .body(containsString("Watched"))
                .body(containsString("★★★★☆"))
                .body(containsString("Update rating"))
                .body(containsString("Remove from Watched"));

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/media/movie/" + extNone)
                .then()
                .statusCode(200)
                .body(containsString("Add to Wishlist"))
                .body(containsString("Or mark as Watched"));
    }

    @Test
    public void testNavContainsWatchedLinkWhenAuthed() {
        String email = "nav-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String jwt = registerAndGetJwt(email, "password123");

        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/app")
                .then()
                .statusCode(200)
                .body(containsString("href=\"/wishlist\""))
                .body(containsString("href=\"/watched\""))
                .body(containsString(">Watched<"));

        given().when().get("/").then().statusCode(200).body(not(containsString("href=\"/watched\"")));
    }
}
