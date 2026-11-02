package com.nn.walletapp_api.shared.security.ratelimit.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @Positive(message = "Capacity must be strictly positive")
        long capacity,
        @Positive(message = "Refill tokens must be strictly positive")
        long refillTokens,
        @Positive(message = "Refill duration in seconds must be strictly positive")
        long refillDurationSeconds
) {
}
