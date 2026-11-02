package com.nn.walletapp_api.shared.audit.listener;

import com.nn.walletapp_api.shared.audit.domain.CustomRevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

public class SecurityContextRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        if (revisionEntity instanceof CustomRevisionEntity customRevision) {
            resolveCurrentSubject().ifPresent(customRevision::setAuthorId);
        }
    }

    private Optional<UUID> resolveCurrentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return Optional.ofNullable(authentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(token -> token.getToken().getSubject())
                .flatMap(this::safeParseUuid);
    }

    private Optional<UUID> safeParseUuid(String subject) {
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
