package com.nn.walletapp_api.transaction.exception;

public class InvalidTransactionStateException extends RuntimeException {

    public InvalidTransactionStateException(String message) {
        super(message);
    }
}