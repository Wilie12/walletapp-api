package com.nn.walletapp_api.transaction.exception;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String message) {
        super(message);
    }

    public TransactionNotFoundException(UUID transactionId) {
        super(String.format("Transaction not found with ID: %s", transactionId));
    }
}