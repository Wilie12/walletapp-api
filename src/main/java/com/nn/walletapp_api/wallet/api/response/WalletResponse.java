package com.nn.walletapp_api.wallet.api.response;

import com.nn.walletapp_api.wallet.domain.WalletStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        UUID ownerSub,
        String label,
        BigDecimal balance,
        String currency,
        WalletStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
