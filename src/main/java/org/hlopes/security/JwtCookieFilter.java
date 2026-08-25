package org.hlopes.security;

import java.io.IOException;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;

/**
 * Bridges HttpOnly cookie `jwt` to `Authorization: Bearer` so SmallRye JWT + @RolesAllowed work for
 * Qute pages. Pure Tailwind pages POST form → AuthPageResource sets cookie; subsequent GET /app
 * sends cookie → filter injects header → JWT validated.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtCookieFilter implements ContainerRequestFilter {

    private static final String COOKIE_NAME = "jwt";
    private static final String AUTH_HEADER = "Authorization";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // If Authorization already present (API clients), keep it
        if (requestContext.getHeaderString(AUTH_HEADER) != null
                && !requestContext.getHeaderString(AUTH_HEADER).isBlank()) {
            return;
        }
        Cookie cookie = requestContext.getCookies().get(COOKIE_NAME);

        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            // Inject Bearer header for SmallRye JWT
            requestContext.getHeaders().putSingle(AUTH_HEADER, "Bearer " + cookie.getValue());
        }
    }
}
