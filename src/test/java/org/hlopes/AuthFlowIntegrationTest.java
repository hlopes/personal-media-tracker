package org.hlopes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.UUID;

import org.hlopes.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
public class AuthFlowIntegrationTest {

    @Inject
    UserRepository userRepository;

    @Test
    public void testFullFlowWithVerification() {
        String email = "full-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "password123";

        // Register
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        // Retrieve token from DB (simulates clicking email link / Mailpit)
        String token = userRepository.findByEmail(email).orElseThrow().verificationToken;
        // Verify
        given().when()
                .get("/api/auth/verify?token=" + token)
                .then()
                .statusCode(200)
                .body("verified", is(true));

        // Login
        String jwt = given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("tokenType", is("Bearer"))
                .extract()
                .path("accessToken");

        // /api/me with JWT should succeed
        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me")
                .then()
                .statusCode(200)
                .body("email", is(email.toLowerCase()))
                .body("verified", is(true));

        // /api/me without JWT -> 401 already tested
        // Duplicate register -> 409
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(409);
    }

    @Test
    public void testEmailNormalization() {
        String emailUpper = "Case-" + UUID.randomUUID().toString().substring(0, 6) + "@EXAMPLE.COM";
        String password = "password123";

        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + emailUpper + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        // second register with lower case should conflict
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + emailUpper.toLowerCase() + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(409);
    }
}
