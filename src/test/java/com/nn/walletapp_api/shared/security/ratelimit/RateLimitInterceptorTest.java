package com.nn.walletapp_api.shared.security.ratelimit;

import com.nn.walletapp_api.shared.config.WebMvcConfig;
import com.nn.walletapp_api.shared.security.SecurityConfig;
import com.nn.walletapp_api.support.SecurityTestUtils;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RateLimitInterceptorTest.TestRateLimitController.class)
@Import({
        SecurityConfig.class,
        WebMvcConfig.class,
        RateLimitInterceptorTest.TestRateLimitController.class,
})
public class RateLimitInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    @MockitoBean
    private Clock clock;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-31T22:00:00Z");

    @BeforeEach
    void setUpClock() {
        given(clock.instant()).willReturn(FIXED_INSTANT);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }

    @RestController
    static class TestRateLimitController {
        @GetMapping("/api/v1/wallets/me")
        public String getWallet() {
            return "wallet";
        }

        @PostMapping("/api/v1/transactions/top-up")
        public String topUp() {
            return "top-up";
        }

        @PatchMapping("/api/v1/admin/wallets/{id}/status")
        public String updateStatus(@PathVariable String id) {
            return "status-updated";
        }
    }

    @Test
    @DisplayName("Should allow request and set X-RateLimit-Remaining header when tokens available")
    void shouldAllowRequestWhenTokensAvailable() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(rateLimitingService.isEnabled()).willReturn(true);
        given(rateLimitingService.tryConsume("usr:" + userId, 1L))
                .willReturn(ConsumptionProbe.consumed(9, 9));

        // when / then
        mockMvc.perform(get("/api/v1/wallets/me")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
    }

    @Test
    @DisplayName("Should apply K=5 token cost for financial transaction mutation endpoint")
    void shouldApplyHigherCostForTransactions() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(rateLimitingService.isEnabled()).willReturn(true);
        given(rateLimitingService.tryConsume("usr:" + userId, 5L))
                .willReturn(ConsumptionProbe.consumed(5, 5));

        // when / then
        mockMvc.perform(post("/api/v1/transactions/top-up")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "5"));
    }

    @Test
    @DisplayName("Should apply K=2 token cost for wallet status update endpoint")
    void shouldApplyCostForStatusUpdate() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID targetWalletId = UUID.randomUUID();
        given(rateLimitingService.isEnabled()).willReturn(true);
        given(rateLimitingService.tryConsume("usr:" + userId, 2L))
                .willReturn(ConsumptionProbe.consumed(8, 8));

        // when / then
        mockMvc.perform(patch("/api/v1/admin/wallets/" + targetWalletId + "/status")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "8"));
    }

    @Test
    @DisplayName("Should reject request with 429, Retry-After header and ProblemDetail when tokens depleted")
    void shouldRejectWith429WhenTokensDepleted() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        given(rateLimitingService.isEnabled()).willReturn(true);
        given(rateLimitingService.tryConsume(eq("usr:" + userId), anyLong()))
                .willReturn(ConsumptionProbe.rejected(0, 5_000_000_000L, 0)); // 5 sekund do regeneracji

        // when / then
        mockMvc.perform(get("/api/v1/wallets/me")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value("Quota exceeded. Please retry after 5 seconds."))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T22:00:00Z"))
                .andExpect(jsonPath("$.instance").value("/api/v1/wallets/me"));
    }
}
