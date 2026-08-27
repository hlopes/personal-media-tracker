package org.hlopes.resource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.microprofile.jwt.JsonWebToken;

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
    JsonWebToken jwt;

    @Inject
    Template index;

    @Inject
    Template app;

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

            TemplateInstance instance = app.data("email", email).data("currentUser", email);

            return Response.ok(instance).build();
        } catch (Exception e) {
            String msg = URLEncoder.encode("Please login to access your library", StandardCharsets.UTF_8);

            return Response.seeOther(URI.create("/login?error=" + msg)).build();
        }
    }
}
