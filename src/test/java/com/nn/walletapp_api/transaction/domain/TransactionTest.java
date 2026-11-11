package com.nn.walletapp_api.transaction.domain;

import com.nn.walletapp_api.transaction.exception.InvalidTransactionStateException;
import com.nn.walletapp_api.transaction.exception.SelfTransferException;
import com.nn.walletapp_api.transaction.exception.TransactionOwnershipException;
import com.nn.walletapp_api.wallet.exception.InvalidAmountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TransactionTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-11T18:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Should initialize PENDING top-up transaction with null senderWalletId and scaled amount")
    void shouldInitializePendingTopUpTransaction() {
        // given
        String referenceNumber = Transaction.formatReferenceNumber(1L);
        UUID idempotencyKey = UUID.randomUUID();
        UUID recipientWalletId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250.50");
        Instant creationTime = Instant.now(fixedClock);

        // when
        Transaction transaction = Transaction.createTopUp(
                referenceNumber,
                idempotencyKey,
                recipientWalletId,
                amount,
                "  Initial funding  ",
                creationTime
        );

        // then
        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getReferenceNumber()).isEqualTo("TRX0000001");
        assertThat(transaction.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(transaction.getSenderWalletId()).isNull();
        assertThat(transaction.getRecipientWalletId()).isEqualTo(recipientWalletId);
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("250.5000"));
        assertThat(transaction.getType()).isEqualTo(TransactionType.TOP_UP);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.isTerminal()).isFalse();
        assertThat(transaction.getDescription()).isEqualTo("Initial funding");
        assertThat(transaction.getCreatedAt()).isEqualTo(creationTime);
    }

    @Test
    @DisplayName("Should use default description when null or blank description provided for top-up")
    void shouldUseDefaultDescriptionForTopUpWhenBlank() {
        // given
        Instant creationTime = Instant.now(fixedClock);

        // when
        Transaction transaction = Transaction.createTopUp(
                "TRX0000002",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.0000"),
                "   ",
                creationTime
        );

        // then
        assertThat(transaction.getDescription()).isEqualTo("Wallet Top-Up");
    }

    @Test
    @DisplayName("Should initialize PENDING transfer transaction between two distinct wallets")
    void shouldInitializePendingTransferTransaction() {
        // given
        String referenceNumber = Transaction.formatReferenceNumber(42L);
        UUID idempotencyKey = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID recipientWalletId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("75.125");
        Instant creationTime = Instant.now(fixedClock);

        // when
        Transaction transaction = Transaction.createTransfer(
                referenceNumber,
                idempotencyKey,
                senderWalletId,
                recipientWalletId,
                amount,
                null,
                creationTime
        );

        // then
        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getReferenceNumber()).isEqualTo("TRX0000042");
        assertThat(transaction.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(transaction.getSenderWalletId()).isEqualTo(senderWalletId);
        assertThat(transaction.getRecipientWalletId()).isEqualTo(recipientWalletId);
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("75.1250"));
        assertThat(transaction.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getDescription()).isEqualTo("P2P Transfer");
        assertThat(transaction.getCreatedAt()).isEqualTo(creationTime);
    }

    @Test
    @DisplayName("Should throw SelfTransferException when sender and recipient wallet IDs are identical (OWASP API6)")
    void shouldRejectSelfTransfer() {
        // given
        UUID sameWalletId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> Transaction.createTransfer(
                "TRX0000003",
                UUID.randomUUID(),
                sameWalletId,
                sameWalletId,
                new BigDecimal("50.0000"),
                "Self transfer attempt",
                Instant.now(fixedClock)
        ))
                .isInstanceOf(SelfTransferException.class)
                .hasMessageContaining(sameWalletId.toString());
    }

    @Test
    @DisplayName("Should throw InvalidAmountException when creating transaction with zero, negative, or null amount")
    void shouldRejectInvalidAmounts() {
        // given
        UUID idempotencyKey = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Instant now = Instant.now(fixedClock);

        // when & then
        assertThatThrownBy(() -> Transaction.createTopUp("TRX0000001", idempotencyKey, recipientId, BigDecimal.ZERO, null, now))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("strictly positive");

        assertThatThrownBy(() -> Transaction.createTopUp("TRX0000001", idempotencyKey, recipientId, new BigDecimal("-0.0001"), null, now))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("strictly positive");

        assertThatThrownBy(() -> Transaction.createTopUp("TRX0000001", idempotencyKey, recipientId, null, null, now))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    @DisplayName("Should transition state from PENDING to COMPLETED and lock into terminal state")
    void shouldTransitionFromPendingToCompletedAndLockTerminalState() {
        // given
        Transaction transaction = Transaction.createTopUp(
                "TRX0000010",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.0000"),
                "Top up",
                Instant.now(fixedClock)
        );

        // when
        transaction.markCompleted();

        // then
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.isTerminal()).isTrue();

        // when & then (immutability of terminal state)
        assertThatThrownBy(transaction::markFailed)
                .isInstanceOf(InvalidTransactionStateException.class)
                .hasMessageContaining("from COMPLETED to FAILED");

        assertThatThrownBy(transaction::markCompleted)
                .isInstanceOf(InvalidTransactionStateException.class)
                .hasMessageContaining("from COMPLETED to COMPLETED");
    }

    @Test
    @DisplayName("Should transition state from PENDING to FAILED and lock into terminal state")
    void shouldTransitionFromPendingToFailedAndLockTerminalState() {
        // given
        Transaction transaction = Transaction.createTopUp(
                "TRX0000011",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.0000"),
                "Top up",
                Instant.now(fixedClock)
        );

        // when
        transaction.markFailed();

        // then
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.isTerminal()).isTrue();

        // when & then (immutability of terminal state)
        assertThatThrownBy(transaction::markCompleted)
                .isInstanceOf(InvalidTransactionStateException.class)
                .hasMessageContaining("from FAILED to COMPLETED");

        assertThatThrownBy(transaction::markFailed)
                .isInstanceOf(InvalidTransactionStateException.class)
                .hasMessageContaining("from FAILED to FAILED");
    }

    @Test
    @DisplayName("Should verify participation for both sender and recipient wallets and reject outsiders (IDOR guard)")
    void shouldVerifyParticipationAndRejectOutsiders() {
        // given
        UUID senderWalletId = UUID.randomUUID();
        UUID recipientWalletId = UUID.randomUUID();
        UUID attackerWalletId = UUID.randomUUID();

        Transaction transfer = Transaction.createTransfer(
                "TRX0000099",
                UUID.randomUUID(),
                senderWalletId,
                recipientWalletId,
                new BigDecimal("25.0000"),
                "Payment",
                Instant.now(fixedClock)
        );

        // when & then
        assertThat(transfer.isParticipant(senderWalletId)).isTrue();
        assertThat(transfer.isParticipant(recipientWalletId)).isTrue();
        assertThat(transfer.isParticipant(attackerWalletId)).isFalse();
        assertThat(transfer.isParticipant(null)).isFalse();

        transfer.verifyParticipation(senderWalletId);
        transfer.verifyParticipation(recipientWalletId);

        assertThatThrownBy(() -> transfer.verifyParticipation(attackerWalletId))
                .isInstanceOf(TransactionOwnershipException.class)
                .hasMessageContaining("Security violation");
    }

    @Test
    @DisplayName("Should format sequence number into TRX%07d reference format and reject non-positive numbers")
    void shouldFormatReferenceNumberCorrectly() {
        // given / when / then
        assertThat(Transaction.formatReferenceNumber(1L)).isEqualTo("TRX0000001");
        assertThat(Transaction.formatReferenceNumber(9999999L)).isEqualTo("TRX9999999");

        assertThatThrownBy(() -> Transaction.formatReferenceNumber(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Transaction.formatReferenceNumber(-5L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}