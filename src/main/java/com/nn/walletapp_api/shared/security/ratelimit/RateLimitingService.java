package com.nn.walletapp_api.shared.security.ratelimit;

import com.nn.walletapp_api.shared.security.ratelimit.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitingService {

    private final RateLimitProperties properties;
    private final TimeMeter timeMeter;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingService(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.timeMeter = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return TimeUnit.MILLISECONDS.toNanos(clock.millis());
            }

            @Override
            public boolean isWallClockBased() {
                return true;
            }
        };
    }

    public ConsumptionProbe tryConsume(String key, long tokens) {
        Bucket bucket = resolveBucket(key);
        return bucket.tryConsumeAndReturnRemaining(tokens);
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    private Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, this::createNewBucket);
    }

    private Bucket createNewBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.capacity())
                .refillGreedy(properties.refillTokens(), Duration.ofSeconds(properties.refillDurationSeconds()))
                .build();

        return Bucket.builder()
                .withCustomTimePrecision(this.timeMeter)
                .addLimit(limit)
                .build();
    }
}
