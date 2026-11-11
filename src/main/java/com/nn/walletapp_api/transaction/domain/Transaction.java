package com.nn.walletapp_api.transaction.domain;

import com.nn.walletapp_api.transaction.exception.SelfTransferException;
import com.nn.walletapp_api.transaction.exception.TransactionOwnershipException;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.exception.InvalidAmountException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@ToString(of = {"id", "referenceNumber", "type", "status", "amount"})
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    private static final String REFERENCE_NUMBER_FORMAT = "TRX%07d";
    private static final String DEFAULT_TOP_UP_DESCRIPTION = "Wallet Top-Up";
    private static final String DEFAULT_TRANSFER_DESCRIPTION = "P2P Transfer";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reference_number", nullable = false, unique = true, updatable = false, length = 50)
    private String referenceNumber;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "sender_wallet_id", updatable = false)
    private UUID senderWalletId;

    @Column(name = "recipient_wallet_id", nullable = false, updatable = false)
    private UUID recipientWalletId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = Wallet.BALANCE_SCALE)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    @Column(name = "description", updatable = false, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Transaction createTopUp(String referenceNumber,
                                          UUID idempotencyKey,
                                          UUID recipientWalletId,
                                          BigDecimal amount,
                                          String description,
                                          Instant creationInstant) {
        String validatedReference = validateReferenceNumber(referenceNumber);
        UUID validatedIdempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        UUID validatedRecipientId = Objects.requireNonNull(recipientWalletId, "recipientWalletId must not be null");
        BigDecimal validatedAmount = validatePositiveAmount(amount);
        Instant validatedInstant = Objects.requireNonNull(creationInstant, "creationInstant must not be null");
        String resolvedDescription = resolveDescription(description, DEFAULT_TOP_UP_DESCRIPTION);

        Transaction transaction = new Transaction();
        transaction.id = UUID.randomUUID();
        transaction.referenceNumber = validatedReference;
        transaction.idempotencyKey = validatedIdempotencyKey;
        transaction.senderWalletId = null;
        transaction.recipientWalletId = validatedRecipientId;
        transaction.amount = validatedAmount;
        transaction.type = TransactionType.TOP_UP;
        transaction.status = TransactionStatus.PENDING;
        transaction.description = resolvedDescription;
        transaction.createdAt = validatedInstant;
        return transaction;
    }

    public static Transaction createTransfer(String referenceNumber,
                                             UUID idempotencyKey,
                                             UUID senderWalletId,
                                             UUID recipientWalletId,
                                             BigDecimal amount,
                                             String description,
                                             Instant creationInstant) {
        String validatedReference = validateReferenceNumber(referenceNumber);
        UUID validatedIdempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        UUID validatedSenderId = Objects.requireNonNull(senderWalletId, "senderWalletId must not be null");
        UUID validatedRecipientId = Objects.requireNonNull(recipientWalletId, "recipientWalletId must not be null");

        if (validatedSenderId.equals(validatedRecipientId)) {
            throw new SelfTransferException(validatedSenderId);
        }

        BigDecimal validatedAmount = validatePositiveAmount(amount);
        Instant validatedInstant = Objects.requireNonNull(creationInstant, "creationInstant must not be null");
        String resolvedDescription = resolveDescription(description, DEFAULT_TRANSFER_DESCRIPTION);

        Transaction transaction = new Transaction();
        transaction.id = UUID.randomUUID();
        transaction.referenceNumber = validatedReference;
        transaction.idempotencyKey = validatedIdempotencyKey;
        transaction.senderWalletId = validatedSenderId;
        transaction.recipientWalletId = validatedRecipientId;
        transaction.amount = validatedAmount;
        transaction.type = TransactionType.TRANSFER;
        transaction.status = TransactionStatus.PENDING;
        transaction.description = resolvedDescription;
        transaction.createdAt = validatedInstant;
        return transaction;
    }

    public static String formatReferenceNumber(long sequenceNumber) {
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("Sequence number must be strictly positive");
        }
        return String.format(REFERENCE_NUMBER_FORMAT, sequenceNumber);
    }

    public void markCompleted() {
        this.status = this.status.transitionTo(TransactionStatus.COMPLETED);
    }

    public void markFailed() {
        this.status = this.status.transitionTo(TransactionStatus.FAILED);
    }

    public boolean isParticipant(UUID walletId) {
        return Optional.ofNullable(walletId)
                .map(id -> id.equals(this.recipientWalletId) || id.equals(this.senderWalletId))
                .orElse(false);
    }

    public void verifyParticipation(UUID walletId) {
        Objects.requireNonNull(walletId, "walletId must not be null");
        if (!isParticipant(walletId)) {
            throw new TransactionOwnershipException(
                    String.format("Security violation: wallet %s is not a participant of transaction %s",
                            walletId, this.id)
            );
        }
    }

    public boolean isTerminal() {
        return this.status.isTerminal();
    }

    private static String validateReferenceNumber(String referenceNumber) {
        return Optional.ofNullable(referenceNumber)
                .map(String::trim)
                .filter(ref -> !ref.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("referenceNumber must not be null or blank"));
    }

    private static BigDecimal validatePositiveAmount(BigDecimal amount) {
        return Optional.ofNullable(amount)
                .filter(a -> a.compareTo(BigDecimal.ZERO) > 0)
                .map(a -> a.setScale(Wallet.BALANCE_SCALE, Wallet.DEFAULT_ROUNDING))
                .orElseThrow(() -> new InvalidAmountException("Transaction amount must be strictly positive and non-null"));
    }

    private static String resolveDescription(String description, String defaultFallback) {
        return Optional.ofNullable(description)
                .map(String::trim)
                .filter(desc -> !desc.isBlank())
                .orElse(defaultFallback);
    }
}