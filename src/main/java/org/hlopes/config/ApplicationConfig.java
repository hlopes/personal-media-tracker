package org.hlopes.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "mediashelf")
public interface ApplicationConfig {

    Verification verification();

    Jwt jwt();

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
}
