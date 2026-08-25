package org.hlopes.resource;

import java.net.URI;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PageResource {

    @Inject
    Template index;

    @Inject
    Template app;

    @Inject
    @Location("auth/login")
    Template auth_login;

    @Inject
    @Location("auth/register")
    Template auth_register;

    @Inject
    @Location("auth/verification-sent")
    Template auth_verification_sent;

    @Inject
    JsonWebToken jwt;

    @GET
    @PermitAll
    public TemplateInstance getIndex() {
        String email = null;

        try {
            if (jwt != null && jwt.getSubject() != null) {
                email = jwt.getSubject();
            }

        } catch (Exception ignored) {
        }

        return index.data("currentUser", email);
    }

    @GET
    @Path("app")
    @PermitAll
    public Response getApp() {
        try {
            String email = jwt != null ? jwt.getSubject() : null;

            if (email == null || email.isBlank()) {
                throw new NotAuthorizedException("Not logged in");
            }
            // Also check that the JWT has the expected group/issuer; if the token is invalid/expired,
            // SmallRye would have already rejected it via the filter, but with PermitAll we need to
            // handle it.
            // Rendering the app template with the email proves the cookie was valid.
            TemplateInstance instance = app.data("email", email).data("currentUser", email);

            return Response.ok(instance).build();

        } catch (Exception e) {
            // For browser HTML, redirect to login instead of raw 401
            String msg = java.net.URLEncoder.encode(
                    "Please login to access your library", java.nio.charset.StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        }
    }

    @GET
    @Path("login")
    @PermitAll
    public TemplateInstance getLogin(
            @QueryParam("error") String error,
            @QueryParam("message") String message,
            @QueryParam("email") String email) {
        return auth_login
                .data("error", error)
                .data("message", message)
                .data("email", email)
                .data("currentUser", null);
    }

    @GET
    @Path("register")
    @PermitAll
    public TemplateInstance getRegister(@QueryParam("error") String error, @QueryParam("email") String email) {
        return auth_register.data("error", error).data("email", email).data("currentUser", null);
    }

    @GET
    @Path("verification-sent")
    @PermitAll
    public TemplateInstance getVerificationSent(
            @QueryParam("email") String email,
            @QueryParam("message") String message,
            @QueryParam("error") String error) {
        return auth_verification_sent
                .data("email", email)
                .data("resendMessage", message)
                .data("resendError", error)
                .data("currentUser", null);
    }
}
