package com.nn.walletapp_api.support;

import com.nn.walletapp_api.shared.api.advice.GlobalExceptionHandler;
import com.nn.walletapp_api.shared.config.WebMvcConfig;
import com.nn.walletapp_api.shared.security.SecurityConfig;
import com.nn.walletapp_api.shared.security.ratelimit.RateLimitingService;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        WebMvcConfig.class,
        GlobalExceptionHandler.class
})
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @MockitoBean
    protected RateLimitingService rateLimitingService;

    @MockitoBean
    protected Clock clock;

    protected static final Instant FIXED_INSTANT = Instant.parse("2026-10-31T12:00:00Z");

    @BeforeEach
    void setupBaseMocks() {
        given(clock.instant()).willReturn(FIXED_INSTANT);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        given(rateLimitingService.isEnabled()).willReturn(true);
        given(rateLimitingService.tryConsume(anyString(), anyLong()))
                .willReturn(ConsumptionProbe.consumed(100, 100));
    }
}
