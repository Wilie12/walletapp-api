package com.nn.walletapp_api.shared.security.ratelimit;

import com.nn.walletapp_api.shared.security.ratelimit.config.RateLimitProperties;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

public class RateLimitingServiceTest {

    private final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private static final RateLimitProperties DEFAULT_PROPERTIES = new RateLimitProperties(true, 10, 10, 60);

    private RateLimitingService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitingService(DEFAULT_PROPERTIES, FIXED_CLOCK);
    }

    @Test
    @DisplayName("Should allow consuming tokens within configured capacity")
    void shouldAllowConsumingTokensWithinCapacity() {
        // given / when
        ConsumptionProbe probe = service.tryConsume("user-1", 5);

        // then
        assertThat(probe.isConsumed()).isTrue();
        assertThat(probe.getRemainingTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should reject token consumption when requested amount exceeds remaining capacity")
    void shouldRejectConsumptionWhenCapacityExceeded() {
        // given
        service.tryConsume("user-1", 10);

        // when
        ConsumptionProbe rejectedProbe = service.tryConsume("user-1", 1);

        // then
        assertThat(rejectedProbe.isConsumed()).isFalse();
        assertThat(rejectedProbe.getRemainingTokens()).isZero();
        assertThat(rejectedProbe.getNanosToWaitForRefill()).isPositive();
    }

    @Test
    @DisplayName("Should isolate bucket counters per unique client key")
    void shouldIsolateBucketsPerClientKey() {
        // given
        service.tryConsume("user-1", 10);

        // when
        ConsumptionProbe userTwoProbe = service.tryConsume("user-2", 3);

        // then
        assertThat(userTwoProbe.isConsumed()).isTrue();
        assertThat(userTwoProbe.getRemainingTokens()).isEqualTo(7);
    }
}
