package com.nn.walletapp_api.transaction.api.response;

import com.nn.walletapp_api.transaction.domain.TransactionStatus;
import com.nn.walletapp_api.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String referenceNumber,
        UUID idempotencyKey,
        UUID senderWalletId,
        UUID recipientWalletId,
        BigDecimal amount,
        TransactionType type,
        TransactionStatus status,
        String description,
        Instant createdAt
) {
}