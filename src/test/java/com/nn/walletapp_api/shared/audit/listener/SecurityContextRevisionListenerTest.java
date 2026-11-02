package com.nn.walletapp_api.shared.audit.listener;

import com.nn.walletapp_api.shared.audit.domain.CustomRevisionEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityContextRevisionListenerTest {

    private final SecurityContextRevisionListener listener = new SecurityContextRevisionListener();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should populate authorId in CustomRevisionEntity when JWT is present in SecurityContext")
    void shouldPopulateAuthorIdWhenJwtPresent() {
        // given
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "RS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        CustomRevisionEntity revisionEntity = new CustomRevisionEntity();

        // when
        listener.newRevision(revisionEntity);

        // then
        assertThat(revisionEntity.getAuthorId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should leave authorId null when SecurityContext has no authentication")
    void shouldLeaveAuthorIdNullWhenNoAuthentication() {
        // given
        SecurityContextHolder.clearContext();
        CustomRevisionEntity revisionEntity = new CustomRevisionEntity();

        // when
        listener.newRevision(revisionEntity);

        // then
        assertThat(revisionEntity.getAuthorId()).isNull();
    }
}
