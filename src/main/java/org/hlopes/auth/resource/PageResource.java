package org.hlopes.auth.resource;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.auth.service.AuthService;
import org.hlopes.config.ApplicationConfig;
import org.hlopes.util.ErrorUtil;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PageResource {

    @Inject
    ApplicationConfig applicationConfig;

    @Inject
    JsonWebToken jwt;

    @Inject
    AuthService authService;

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
    @Location("auth/verify-result")
    Template authVerifyResult;

    @GET
    @Path("/login")
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
    @Path("/register")
    @PermitAll
    public TemplateInstance getRegister(@QueryParam("error") String error, @QueryParam("email") String email) {
        return auth_register.data("error", error).data("email", email).data("currentUser", null);
    }

    @GET
    @Path("/verification-sent")
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
            String msg = ErrorUtil.extractError(e);

            return authVerifyResult.data("success", false).data("error", msg).data("currentUser", null);
        }
    }
}
