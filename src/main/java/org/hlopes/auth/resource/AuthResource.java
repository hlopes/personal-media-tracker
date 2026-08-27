package org.hlopes.auth.resource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hlopes.auth.dto.UserResponse;
import org.hlopes.auth.mapper.UserMapper;
import org.hlopes.auth.service.AuthService;
import org.hlopes.util.ErrorUtil;

import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/**
 * Dedicated authentication & authorization resource. Handles both JSON API (under /api) and
 * form-based browser flows (Qute pages). Qute page rendering (GET /login, GET /register, etc.)
 * lives in PageResource.
 */
@Path("/")
@Tag(name = "Authentication", description = "Register, verify email and login (JSON + form)")
public class AuthResource {

    private static final String JWT_COOKIE = "jwt";

    @Inject
    AuthService authService;

    @Inject
    UserMapper userMapper;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/api/me")
    @RolesAllowed("User")
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = "Me", description = "Current authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get current user",
            description =
                    "Protected endpoint - requires Bearer JWT (or jwt cookie via JwtCookieFilter). Use to test auth flow: Register → Verify → Login → Authorize → GET /api/me should return 200.")
    public UserResponse me() {
        String email = jwt.getSubject();

        return authService.getUserOrNotFound(email);
    }

    @POST
    @Path("/api/auth/register")
    @PermitAll
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response registerForm(@FormParam("email") String email, @FormParam("password") String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Response.seeOther(URI.create("/register?error=" + encode("Email and password required")))
                    .build();
        }

        if (password.length() < 8) {
            return Response.seeOther(URI.create("/register?error="
                            + encode("Password must be at least 8 characters")
                            + "&email="
                            + encode(email)))
                    .build();
        }

        try {
            authService.register(email, password);

            return Response.seeOther(URI.create(
                            "/verification-sent?email=" + encode(email.trim().toLowerCase())))
                    .build();
        } catch (WebApplicationException e) {
            String msg = ErrorUtil.extractError(e);

            if (e.getResponse().getStatus() == 409) {
                msg = "Email already registered.";
            }

            return Response.seeOther(URI.create("/register?error=" + encode(msg) + "&email=" + encode(email)))
                    .build();
        } catch (Exception e) {
            Log.error("Register form error", e);

            return Response.seeOther(URI.create("/register?error=" + encode("Unexpected error")))
                    .build();
        }
    }

    @POST
    @Path("/api/auth/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response loginForm(@FormParam("email") String email, @FormParam("password") String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Response.seeOther(URI.create(
                            "/login?error=" + encode("Email and password required") + "&email=" + encode(email)))
                    .build();
        }

        try {
            var resp = authService.login(email, password);
            NewCookie cookie = new NewCookie.Builder(JWT_COOKIE)
                    .value(resp.accessToken())
                    .path("/")
                    .maxAge((int) resp.expiresIn())
                    .httpOnly(true)
                    .secure(false)
                    .sameSite(NewCookie.SameSite.LAX)
                    .build();
            Log.infof("Login cookie set for %s", email);

            return Response.seeOther(URI.create("/app")).cookie(cookie).build();
        } catch (WebApplicationException e) {
            String msg = ErrorUtil.extractError(e);
            int status = e.getResponse().getStatus();

            if (status == 403) {
                msg = "Email not verified — check your inbox for the verification link.";

            } else if (status == 401) {
                msg = "Invalid email or password.";
            }

            return Response.seeOther(URI.create("/login?error=" + encode(msg) + "&email=" + encode(email)))
                    .build();
        } catch (Exception e) {
            Log.error("Login form error", e);

            return Response.seeOther(URI.create("/login?error=" + encode("Unexpected error, try again")))
                    .build();
        }
    }

    @POST
    @Path("/api/auth/resend-verification")
    @PermitAll
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response resendForm(@FormParam("email") String email) {
        if (email == null || email.isBlank()) {
            return Response.seeOther(URI.create("/verification-sent?error=" + encode("Email required")))
                    .build();
        }

        try {
            authService.resendVerification(email);

            return Response.seeOther(URI.create("/verification-sent?email="
                            + encode(email)
                            + "&message="
                            + encode("Verification email resent — check inbox and server logs")))
                    .build();
        } catch (WebApplicationException e) {
            String msg = ErrorUtil.extractError(e);

            return Response.seeOther(URI.create("/verification-sent?email=" + encode(email) + "&error=" + encode(msg)))
                    .build();
        }
    }

    @POST
    @Path("/api/auth/logout")
    @PermitAll
    public Response logout() {
        NewCookie clear = new NewCookie.Builder(JWT_COOKIE)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .build();

        return Response.seeOther(URI.create("/login?message=" + encode("Logged out")))
                .cookie(clear)
                .build();
    }

    private String encode(String s) {
        if (s == null) {
            return "";
        }

        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
