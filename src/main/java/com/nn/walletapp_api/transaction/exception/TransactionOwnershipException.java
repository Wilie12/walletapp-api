package com.nn.walletapp_api.transaction.exception;

public class TransactionOwnershipException extends RuntimeException {

    public TransactionOwnershipException(String message) {
        super(message);
    }
}