package com.nn.walletapp_api.transaction.domain;

import com.nn.walletapp_api.transaction.exception.InvalidTransactionStateException;

import java.util.Set;

public enum TransactionStatus {
    PENDING {
        @Override
        public Set<TransactionStatus> allowedTransitions() {
            return Set.of(COMPLETED, FAILED);
        }
    },
    COMPLETED {
        @Override
        public Set<TransactionStatus> allowedTransitions() {
            return Set.of();
        }
    },
    FAILED {
        @Override
        public Set<TransactionStatus> allowedTransitions() {
            return Set.of();
        }
    };

    public abstract Set<TransactionStatus> allowedTransitions();

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }

    public TransactionStatus transitionTo(TransactionStatus targetStatus) {
        if (!allowedTransitions().contains(targetStatus)) {
            throw new InvalidTransactionStateException(
                    String.format("Invalid transaction state transition from %s to %s", this, targetStatus)
            );
        }
        return targetStatus;
    }
}