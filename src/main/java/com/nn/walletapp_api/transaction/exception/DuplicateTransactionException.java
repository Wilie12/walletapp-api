package com.nn.walletapp_api.transaction.exception;

import java.util.UUID;

public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(String message) {
        super(message);
    }

    public DuplicateTransactionException(UUID idempotencyKey) {
        super(String.format("Transaction with idempotency key '%s' has already been processed", idempotencyKey));
    }
}