package org.hlopes.auth.resource;

import java.util.Map;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.hlopes.auth.dto.LoginRequest;
import org.hlopes.auth.dto.LoginResponse;
import org.hlopes.auth.dto.MessageResponse;
import org.hlopes.auth.dto.RegisterRequest;
import org.hlopes.auth.dto.ResendVerificationRequest;
import org.hlopes.auth.mapper.UserMapper;
import org.hlopes.auth.service.AuthService;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Dedicated authentication & authorization resource. Handles both JSON API (under /api) and
 * form-based browser flows (Qute pages). Qute page rendering (GET /login, GET /register, etc.)
 * lives in PageResource.
 */
@Path("/")
@Tag(name = "Helper Authentication", description = "Helpers for Register, verify email and login (JSON)")
public class HelperResource {

    @Inject
    AuthService authService;

    @Inject
    UserMapper userMapper;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/api/helpers/auth/register")
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

    @GET
    @Path("/api/helpers/auth/verify")
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

    @POST
    @Path("/api/helpers/auth/resend-verification")
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

    @POST
    @Path("/api/helpers/auth/login")
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
}
