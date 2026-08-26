package org.hlopes.resource;

import java.net.URI;
import java.util.Map;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hlopes.dto.*;
import org.hlopes.mapper.UserMapper;
import org.hlopes.repository.UserRepository;
import org.hlopes.service.AuthService;

import io.quarkus.logging.Log;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
    UserRepository userRepository;

    @Inject
    UserMapper userMapper;

    @Inject
    JsonWebToken jwt;

    @Inject
    @Location("auth/verify-result")
    Template authVerifyResult;

    // --- JSON API: register ---

    @POST
    @Path("/api/auth/register")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Register a new user",
            description =
                    "Creates a user with verified=false and sends verification email (also logged to console). Test via Swagger then check logs or Mailpit at http://localhost:8025")
    @APIResponse(responseCode = "201", description = "Registered - check email")
    @APIResponse(responseCode = "409", description = "Email already registered")
    public Response registerJson(@Valid RegisterRequest request) {
        authService.register(request.email(), request.password());

        return Response.status(Response.Status.CREATED)
                .entity(new MessageResponse("registered - check email for verification link (also see server logs)"))
                .build();
    }

    // --- JSON API: verify ---

    @GET
    @Path("/api/auth/verify")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Verify email (JSON)",
            description =
                    "Verifies email ownership using one-time token sent via email. Token is single-use, 24h expiry.")
    @APIResponse(responseCode = "200", description = "Verified")
    @APIResponse(responseCode = "400", description = "Invalid or expired token")
    public Response verifyJson(@QueryParam("token") String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("token query param is required");
        }
        authService.verify(token);

        return Response.ok(Map.of("verified", true, "message", "email verified - you can now login"))
                .build();
    }

    // --- JSON API: resend ---

    @POST
    @Path("/api/auth/resend-verification")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Resend verification email",
            description = "Generates a new token and resends email if user not yet verified")
    @APIResponse(responseCode = "200", description = "Resent")
    public Response resendJson(@Valid ResendVerificationRequest request) {
        authService.resendVerification(request.email());

        return Response.ok(new MessageResponse("verification email resent - check inbox/logs"))
                .build();
    }

    // --- JSON API: login ---

    @POST
    @Path("/api/auth/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Login (JSON)",
            description =
                    "Returns a JWT Bearer token. Use 'Authorize' in Swagger UI with 'Bearer <token>' to call /api/me. Fails with 403 if email not verified.")
    @APIResponse(
            responseCode = "200",
            description = "JWT issued",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @APIResponse(responseCode = "401", description = "Invalid credentials")
    @APIResponse(responseCode = "403", description = "Email not verified")
    public Response loginJson(@Valid LoginRequest request) {
        LoginResponse resp = authService.login(request.email(), request.password());

        return Response.ok(resp).build();
    }

    // --- Authorization: me (requires JWT) ---

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

        return userRepository
                .findByEmail(email)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("user not found"));
    }

    // --- Form: register (browser) ---

    @POST
    @Path("/api/register")
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
            String msg = extractError(e);

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

    // --- Form: login (browser, sets HttpOnly cookie) ---

    @POST
    @Path("/api/login")
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
            String msg = extractError(e);
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

    // --- Form: resend verification (browser) ---

    @POST
    @Path("/api/resend-verification")
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
            String msg = extractError(e);

            return Response.seeOther(URI.create("/verification-sent?email=" + encode(email) + "&error=" + encode(msg)))
                    .build();
        }
    }

    // --- HTML verify (browser, renders Qute) ---

    @GET
    @Path("/verify")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public Object verifyHtml(@QueryParam("token") String token) {
        if (token == null || token.isBlank()) {
            return authVerifyResult
                    .data("success", false)
                    .data("error", "Missing token")
                    .data("currentUser", null);
        }

        try {
            authService.verify(token);

            return authVerifyResult
                    .data("success", true)
                    .data("email", "your email")
                    .data("currentUser", null);

        } catch (WebApplicationException e) {
            String msg = extractError(e);

            return authVerifyResult.data("success", false).data("error", msg).data("currentUser", null);
        }
    }

    // --- Logout (clears cookie) ---

    @POST
    @Path("/api/logout")
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

    private String extractError(WebApplicationException e) {
        try {
            Object entity = e.getResponse().getEntity();

            if (entity instanceof java.util.Map) {
                Object err = ((java.util.Map<?, ?>) entity).get("error");

                if (err != null) {
                    return err.toString();
                }
            }

            if (entity != null) {
                return entity.toString();
            }

        } catch (Exception ignored) {
        }

        if (e.getMessage() != null) {
            return e.getMessage();
        }

        return "Request failed";
    }

    private String encode(String s) {
        if (s == null) {
            return "";
        }

        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);

        } catch (Exception e) {
            return s;
        }
    }
}
