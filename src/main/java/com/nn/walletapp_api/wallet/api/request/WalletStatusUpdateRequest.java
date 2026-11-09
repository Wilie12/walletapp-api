package com.nn.walletapp_api.wallet.api.request;

import com.nn.walletapp_api.wallet.domain.WalletStatus;
import jakarta.validation.constraints.NotNull;

public record WalletStatusUpdateRequest(
        @NotNull(message = "Target wallet status must not be null")
        WalletStatus status
) {
}
