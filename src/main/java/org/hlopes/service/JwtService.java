package org.hlopes.service;

import java.time.Duration;
import java.util.Set;

import org.hlopes.config.ApplicationConfig;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JwtService {

    @Inject
    ApplicationConfig applicationConfig;

    public String generateToken(String email) {
        return Jwt.issuer(applicationConfig.jwt().issuer())
                .subject(email)
                .upn(email)
                .groups(Set.of("User"))
                .audience("mediashelf")
                .claim("email", email)
                .expiresIn(Duration.ofSeconds(applicationConfig.jwt().lifespan()))
                .sign();
    }

    public long getLifespanSeconds() {
        return applicationConfig.jwt().lifespan();
    }
}
