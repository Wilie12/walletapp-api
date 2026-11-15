package com.nn.walletapp_api.transaction.event;

import com.nn.walletapp_api.transaction.domain.Transaction;
import com.nn.walletapp_api.transaction.domain.TransactionStatus;
import com.nn.walletapp_api.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TransactionCompletedEvent(
        UUID transactionId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount
) {
    public TransactionCompletedEvent {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
    }

    public static TransactionCompletedEvent from(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        return new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount()
        );
    }
}