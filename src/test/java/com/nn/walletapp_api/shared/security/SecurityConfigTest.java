package com.nn.walletapp_api.shared.security;

import com.nn.walletapp_api.shared.security.ratelimit.RateLimitingService;
import com.nn.walletapp_api.support.SecurityTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.TestSecuredController.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestSecuredController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private RateLimitingService rateLimitingService;
    @MockitoBean
    private Clock clock;

    @RestController
    static class TestSecuredController {
        @GetMapping("/api/v1/test-secure")
        public String secureEndpoint() {
            return "secure";
        }
    }

    @Test
    @DisplayName("Should reject unauthenticated request with 401 Unauthorized")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        // given / when / then
        mockMvc.perform(get("/api/v1/test-secure"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should allow authenticated request and return OWASP API8 security headers")
    void shouldAllowAuthenticatedAndReturnOwaspHeaders() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when / then
        mockMvc.perform(get("/api/v1/test-secure")
                        .secure(true)
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; sandbox"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }
}