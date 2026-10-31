package com.nn.walletapp_api.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return Optional.ofNullable(jwt.getClaimAsMap(REALM_ACCESS_CLAIM))
                .map(realmAccess -> extractRoles(realmAccess))
                .orElseGet(Collections::emptyList);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRoles(Map<String, Object> realmAccess) {
        return Optional.ofNullable(realmAccess.get(ROLES_CLAIM))
                .filter(Collection.class::isInstance)
                .map(roles -> ((Collection<?>) roles).stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(SimpleGrantedAuthority::new)
                        .map(GrantedAuthority.class::cast)
                        .toList())
                .orElseGet(Collections::emptyList);
    }
}
