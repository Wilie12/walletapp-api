package com.nn.walletapp_api.shared.security.ratelimit;

import com.nn.walletapp_api.support.BaseIntegrationTest;
import com.nn.walletapp_api.support.SecurityTestUtils;
import com.nn.walletapp_api.transaction.repository.TransactionRepository;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("benchmark")
@TestPropertySource(properties = {
        "app.security.rate-limit.enabled=false",
        "app.security.rate-limit.capacity=1",
        "app.security.rate-limit.refill-tokens=1",
        "app.security.rate-limit.refill-duration-seconds=3600"
})
public class RateLimitToggleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("Variant A (Baseline): Should allow unauthenticated requests to baseline benchmark endpoint without rate limiting")
    void shouldAllowUnauthenticatedRequestsToBaselineBenchmarkEndpoint() throws Exception {
        // given / when / then
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/benchmark/baseline"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("X-RateLimit-Remaining"))
                    .andExpect(jsonPath("$.variant").value("A_BASELINE"))
                    .andExpect(jsonPath("$.status").value("OK"));
        }
    }

    @Test
    @DisplayName("Variant B (JWT Only): Should bypass Bucket4j capacity limit when rate-limit toggle is disabled")
    void shouldBypassRateLimiterWhenDisabledViaConfiguration() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when / then (capacity is set to 1, but 3 consecutive requests must succeed with 200 OK)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/wallets/me")
                            .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("X-RateLimit-Remaining"))
                    .andExpect(header().doesNotExist("Retry-After"))
                    .andExpect(jsonPath("$.ownerSub").value(userId.toString()));
        }
    }
}