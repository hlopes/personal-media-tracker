package org.hlopes.auth.resource;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hlopes.auth.service.AuthService;
import org.hlopes.config.ApplicationConfig;
import org.hlopes.util.ErrorUtil;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PageResource {

    private final JsonWebToken jwt;
    private final ApplicationConfig applicationConfig;
    private final AuthService authService;
    private final Template auth_login;
    private final Template auth_register;
    private final Template auth_verification_sent;
    private final Template authVerifyResult;

    public PageResource(
            JsonWebToken jwt,
            ApplicationConfig applicationConfig,
            AuthService authService,
            @Location("auth/login") Template auth_login,
            @Location("auth/register") Template auth_register,
            @Location("auth/verification-sent") Template auth_verification_sent,
            @Location("auth/verify-result") Template authVerifyResult) {
        this.jwt = jwt;
        this.applicationConfig = applicationConfig;
        this.authService = authService;
        this.auth_login = auth_login;
        this.auth_register = auth_register;
        this.auth_verification_sent = auth_verification_sent;
        this.authVerifyResult = authVerifyResult;
    }

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
