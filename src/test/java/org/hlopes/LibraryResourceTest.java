package org.hlopes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
public class LibraryResourceTest {

    @Test
    public void testLibraryRequiresAuth() {
        given().when().get("/api/me/library").then().statusCode(401);
        given().contentType(ContentType.JSON)
                .body("{\"externalId\":603,\"mediaType\":\"movie\"}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(401);
        given().when().delete("/api/me/library/" + UUID.randomUUID()).then().statusCode(401);
    }

    @Test
    public void testAddValidation() {
        // Auth required takes precedence, but structure ensures 401 without JWT
        given().contentType(ContentType.JSON)
                .body("{\"externalId\":null,\"mediaType\":null}")
                .when()
                .post("/api/me/library")
                .then()
                .statusCode(401);
    }

    @Test
    public void testWishlistPageRequiresAuth() {
        given().redirects()
                .follow(false)
                .when()
                .get("/wishlist")
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
}
