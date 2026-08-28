package org.hlopes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.Base64;
import java.util.UUID;

import org.hlopes.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
public class AppFlowTest {

    @Inject
    UserRepository userRepository;

    @Test
    public void testLoginFormThenAppWithCookie() {
        String email = "app-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "password123";

        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/helpers/auth/register")
                .then()
                .statusCode(201);

        String token = userRepository.findByEmail(email).orElseThrow().verificationToken;
        given().when().get("/api/helpers/auth/verify?token=" + token).then().statusCode(200);

        var loginResp = given().redirects()
                .follow(false)
                .contentType(ContentType.URLENC)
                .formParam("email", email)
                .formParam("password", password)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(303)
                .header("Location", containsString("/"))
                .header("Location", not(containsString("/app")))
                .cookie("jwt", notNullValue())
                .extract()
                .response();

        String jwtCookie = loginResp.cookie("jwt");
        System.out.println("JWT cookie: " + jwtCookie.substring(0, 20) + "...");

        String jwt = given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/api/helpers/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        System.out.println("JWT from login: " + jwt.substring(0, 20) + "...");
        String[] parts = jwt.split("\\.");

        if (parts.length == 3) {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            System.out.println("JWT payload: " + payload);
        }

        System.out.println("Testing /api/me with Bearer header");
        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/api/me")
                .then()
                .log()
                .all()
                .statusCode(200);

        System.out.println("Testing /api/me with cookie");
        given().cookie("jwt", jwtCookie)
                .when()
                .get("/api/me")
                .then()
                .log()
                .all()
                .statusCode(200);

        System.out.println("Testing / with Bearer header");
        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/")
                .then()
                .log()
                .all()
                .statusCode(200)
                .body(containsString("Welcome"));

        System.out.println("Testing / with cookie");
        given().cookie("jwt", jwtCookie)
                .when()
                .get("/")
                .then()
                .log()
                .all()
                .statusCode(200)
                .body(containsString("Welcome"))
                .body(containsString(email.toLowerCase()));

        System.out.println("Testing /app with Bearer header");
        given().header("Authorization", "Bearer " + jwt)
                .when()
                .get("/app")
                .then()
                .log()
                .all()
                .statusCode(200)
                .body(containsString("Welcome"));

        System.out.println("Testing /app with cookie");
        given().cookie("jwt", jwtCookie)
                .when()
                .get("/app")
                .then()
                .log()
                .all()
                .statusCode(200)
                .body(containsString("Welcome"))
                .body(containsString(email.toLowerCase()));
    }
}
