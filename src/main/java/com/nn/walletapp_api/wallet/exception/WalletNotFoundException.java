package com.nn.walletapp_api.wallet.exception;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String message) {
        super(message);
    }

    public WalletNotFoundException(UUID walletId) {
        super(String.format("Wallet not found with ID: %s", walletId));
    }
}
