package org.hlopes;

import static io.restassured.RestAssured.given;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import org.hlopes.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
public class ImageIdentificationResourceTest {

    @Inject
    UserRepository userRepository;

    private String loginAsNewUser() {
        String email = "vision-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
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

    @Test
    public void testUploadRequiresAuth() throws Exception {
        File tmp = File.createTempFile("img", ".png");
        Files.write(tmp.toPath(), new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});

        try {
            given().multiPart("image", tmp, "image/png")
                    .when()
                    .post("/api/chat/image")
                    .then()
                    .statusCode(401);
        } finally {
            tmp.delete();
        }
    }

    @Test
    public void testUploadMissingFile() {
        String jwt = loginAsNewUser();

        given().header("Authorization", "Bearer " + jwt)
                .contentType(ContentType.MULTIPART)
                .when()
                .post("/api/chat/image")
                .then()
                .statusCode(400);
    }

    @Test
    public void testUploadUnsupportedType() throws Exception {
        String jwt = loginAsNewUser();
        File tmp = File.createTempFile("note", ".txt");
        Files.writeString(tmp.toPath(), "not an image");

        try {
            given().header("Authorization", "Bearer " + jwt)
                    .multiPart("image", tmp, "text/plain")
                    .when()
                    .post("/api/chat/image")
                    .then()
                    .statusCode(415);
        } finally {
            tmp.delete();
        }
    }
}
