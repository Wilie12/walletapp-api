package com.nn.walletapp_api.shared.security.ratelimit.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true")
        boolean enabled,
        @DefaultValue("10")
        @Positive(message = "Capacity must be strictly positive")
        long capacity,
        @DefaultValue("10")
        @Positive(message = "Refill tokens must be strictly positive")
        long refillTokens,
        @DefaultValue("60")
        @Positive(message = "Refill duration in seconds must be strictly positive")
        long refillDurationSeconds
) {
}