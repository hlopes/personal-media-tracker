package org.hlopes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import java.io.InputStream;
import java.util.UUID;

import org.hlopes.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
public class ImageGuessResourceTest {

    @Inject
    UserRepository userRepository;

    @Test
    public void testGuessImageRequiresAuth() {
        given().multiPart("prompt", "hello")
                .when()
                .post("/api/ai/guess-image")
                .then()
                .statusCode(401);
    }

    @Test
    public void testGuessImageRequiresImagePart() {
        String jwt = login();

        given().header("Authorization", "Bearer " + jwt)
                .multiPart("prompt", "hello")
                .when()
                .post("/api/ai/guess-image")
                .then()
                .statusCode(400);
    }

    @Test
    public void testGuessImageRejectsUnsupportedType() {
        String jwt = login();

        given().header("Authorization", "Bearer " + jwt)
                .multiPart("image", "note.txt", "not an image".getBytes(), "text/plain")
                .when()
                .post("/api/ai/guess-image")
                .then()
                .statusCode(415);
    }

    @Test
    public void testGuessImageRejectsOversize() {
        String jwt = login();

        given().header("Authorization", "Bearer " + jwt)
                .multiPart("image", "big.jpg", new byte[6 * 1024 * 1024], "image/jpeg")
                .when()
                .post("/api/ai/guess-image")
                .then()
                .statusCode(413);
    }

    @Test
    public void testGuessImageFixtureReturnsWorkflowShape() throws Exception {
        String jwt = login();
        byte[] bytes;

        try (InputStream in = getClass().getResourceAsStream("/fixtures/movie-1.jpg")) {
            bytes = in.readAllBytes();
        }

        given().header("Authorization", "Bearer " + jwt)
                .multiPart("image", "movie-1.jpg", bytes, "image/jpeg")
                .when()
                .post("/api/ai/guess-image")
                .then()
                .statusCode(200)
                .body("guessTitle", notNullValue())
                .body("message", notNullValue())
                .body("wishlistStatus", notNullValue())
                .body("suggestAdd", notNullValue());
    }

    private String login() {
        String email = "guess-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "password123";

        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/helpers/auth/register")
                .then()
                .statusCode(201);

        String token = userRepository.findByEmail(email).orElseThrow().verificationToken;

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
}
