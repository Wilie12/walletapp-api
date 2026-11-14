package com.nn.walletapp_api.transaction.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull(message = "Sender wallet ID must not be null")
        UUID senderWalletId,

        @NotNull(message = "Recipient wallet ID must not be null")
        UUID recipientWalletId,

        @NotNull(message = "Transfer amount must not be null")
        @DecimalMin(value = "0.0001", message = "Transfer amount must be strictly positive")
        @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Transaction description must not exceed 255 characters")
        String description
) {
}