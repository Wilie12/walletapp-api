package com.nn.walletapp_api.shared.security.domain;

import java.util.Objects;
import java.util.UUID;

public record RequesterContext(UUID userId, AccessLevel accessLevel) {

    public RequesterContext {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(accessLevel, "accessLevel must not be null");
    }

    public boolean isAdmin() {
        return AccessLevel.ADMIN.equals(this.accessLevel);
    }
}
