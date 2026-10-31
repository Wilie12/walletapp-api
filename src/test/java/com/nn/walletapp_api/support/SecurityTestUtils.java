package com.nn.walletapp_api.support;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SecurityTestUtils {

    private SecurityTestUtils() {// Private constructor to prevent instantiation
    }

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validJwt(UUID userId, String... roles) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder
                        .subject(userId.toString())
                        .claim("realm_access", Map.of("roles", List.of(roles))));
    }

    public static Jwt createJwt(UUID userId, List<String> roles, Clock clock) {
        Instant now = Instant.now(clock);

        return Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .subject(userId.toString())
                .issuer("http://localhost:8081/realms/walletapp-realm")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("realm_access", Map.of("roles", roles))
                .build();
    }

    public static Jwt createJwtWithoutRealmAccess(UUID userId, Clock clock) {
        Instant now = Instant.now(clock);

        return Jwt.withTokenValue("mock-jwt-token-no-roles")
                .header("alg", "RS256")
                .header("typ", "JWT")
                .subject(userId.toString())
                .issuer("http://localhost:8081/realms/walletapp-realm")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claims(claims -> claims.putAll(Collections.emptyMap()))
                .build();
    }
}
