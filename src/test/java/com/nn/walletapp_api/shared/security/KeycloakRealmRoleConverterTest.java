package com.nn.walletapp_api.shared.security;

import com.nn.walletapp_api.support.SecurityTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-10-31T21:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Should extract authorities when realm_access claim contains roles")
    void shouldExtractAuthoritiesWhenRolesPresent() {
        // given
        UUID userId = UUID.randomUUID();
        Jwt jwt = SecurityTestUtils.createJwt(userId, List.of("ROLE_USER", "ROLE_ADMIN"), fixedClock);

        // when
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // then
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should return empty collection when realm_access claim is missing")
    void shouldReturnEmptyCollectionWhenRealmAccessMissing() {
        // given
        UUID userId = UUID.randomUUID();
        Jwt jwt = SecurityTestUtils.createJwtWithoutRealmAccess(userId, fixedClock);

        // when
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // then
        assertThat(authorities).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Should return empty collection when roles list is empty")
    void shouldReturnEmptyCollectionWhenRolesEmpty() {
        // given
        UUID userId = UUID.randomUUID();
        Jwt jwt = SecurityTestUtils.createJwt(userId, Collections.emptyList(), fixedClock);

        // when
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // then
        assertThat(authorities).isNotNull().isEmpty();
    }
}
