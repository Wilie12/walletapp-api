package com.nn.walletapp_api.transaction.exception;

import java.util.UUID;

public class SelfTransferException extends RuntimeException {

    public SelfTransferException(String message) {
        super(message);
    }

    public SelfTransferException(UUID walletId) {
        super(String.format("Self-transfer is not allowed for wallet ID: %s", walletId));
    }
}