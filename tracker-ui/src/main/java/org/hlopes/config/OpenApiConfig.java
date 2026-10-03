package org.hlopes.config;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

import jakarta.ws.rs.core.Application;

@OpenAPIDefinition(
        info =
                @Info(
                        title = "Personal Media Tracker API",
                        version = "0.2.0",
                        description =
                                "Phase 1 - Authentication (register/verify/login + JWT) + Qute pages (Tailwind). Use Swagger UI at /q/swagger-ui to test: Register → check logs/Mailpit for link → Verify → Login → Authorize (Bearer) → GET /api/me. Or use browser at /login and /register."))
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT Bearer token obtained from POST /api/auth/login")
public class OpenApiConfig extends Application {}
