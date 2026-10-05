package org.hlopes.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "mediashelf")
public interface ApplicationConfig {

    Verification verification();

    Jwt jwt();

    Tmdb tmdb();

    interface Verification {

        @WithDefault("24")
        long tokenExpiryHours();

        @WithDefault("http://localhost:8080")
        String baseUrl();
    }

    interface Jwt {

        @WithDefault("mediashelf")
        String issuer();

        @WithDefault("3600")
        long lifespan();
    }

    interface Tmdb {

        @WithDefault("")
        String apiKey();

        @WithDefault("https://api.themoviedb.org/3")
        String baseUrl();

        @WithDefault("https://image.tmdb.org/t/p")
        String imageBaseUrl();

        @WithDefault("3S")
        Duration timeout();
    }
}
