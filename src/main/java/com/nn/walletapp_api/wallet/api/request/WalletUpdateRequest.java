package com.nn.walletapp_api.wallet.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WalletUpdateRequest(
        @NotBlank(message = "Wallet label must not be blank")
        @Size(max = 100, message = "Wallet label must not exceed 100 characters")
        String label
) {
}