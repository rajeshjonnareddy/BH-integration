package com.example.bullhorn.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for Bullhorn API integration.
 * Maps from application.yml bullhorn.* properties.
 */
@ConfigurationProperties(prefix = "bullhorn")
@Validated
public record BullhornProperties(
        @NotNull OAuth oauth,
        @NotNull Api api,
        @NotNull Session session
) {

    public record OAuth(
            @NotBlank String tokenUrl,
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotBlank String refreshToken
    ) {
    }

    public record Api(
            @NotBlank String loginUrl,
            @NotBlank String version
    ) {
    }

    public record Session(
            Duration cacheDuration,
            int maxRetries
    ) {
        public Session {
            if (cacheDuration == null) {
                cacheDuration = Duration.ofMinutes(50);
            }
            if (maxRetries <= 0) {
                maxRetries = 3;
            }
        }
    }
}
