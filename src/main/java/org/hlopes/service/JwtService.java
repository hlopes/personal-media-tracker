package org.hlopes.service;

import java.time.Duration;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JwtService {

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "mediashelf")
    String issuer;

    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan", defaultValue = "3600")
    long lifespanSeconds;

    public String generateToken(String email) {
        return Jwt.issuer(issuer)
                .subject(email)
                .upn(email)
                .groups(Set.of("User"))
                .audience("mediashelf")
                .claim("email", email)
                .expiresIn(Duration.ofSeconds(lifespanSeconds))
                .sign();
    }

    public long getLifespanSeconds() {
        return lifespanSeconds;
    }
}
