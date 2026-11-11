package com.nn.walletapp_api.transaction.repository;

import com.nn.walletapp_api.support.BaseIntegrationTest;
import com.nn.walletapp_api.transaction.domain.Transaction;
import com.nn.walletapp_api.transaction.domain.TransactionStatus;
import com.nn.walletapp_api.transaction.domain.TransactionType;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TransactionRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletRepository walletRepository;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-11T18:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("Should fetch strictly increasing values from transaction_number_seq and format as TRX%07d")
    void shouldFetchMonotonicallyIncreasingSequenceValues() {
        // given / when
        long firstSeq = transactionRepository.getNextReferenceSequenceValue();
        long secondSeq = transactionRepository.getNextReferenceSequenceValue();

        // then
        assertThat(firstSeq).isPositive();
        assertThat(secondSeq).isEqualTo(firstSeq + 1);
        assertThat(Transaction.formatReferenceNumber(secondSeq))
                .startsWith("TRX")
                .hasSize(10);
    }

    @Test
    @DisplayName("Should persist and retrieve top-up transaction by ID and idempotency key")
    void shouldPersistAndRetrieveTopUpTransaction() {
        // given
        Instant now = Instant.now(fixedClock);
        Wallet recipientWallet = walletRepository.saveAndFlush(
                Wallet.createDefault(UUID.randomUUID(), now)
        );
        UUID idempotencyKey = UUID.randomUUID();
        String refNumber = Transaction.formatReferenceNumber(transactionRepository.getNextReferenceSequenceValue());

        Transaction topUp = Transaction.createTopUp(
                refNumber,
                idempotencyKey,
                recipientWallet.getId(),
                new BigDecimal("500.2500"),
                "Initial account top-up",
                now
        );
        topUp.markCompleted();

        // when
        Transaction saved = transactionRepository.saveAndFlush(topUp);
        Optional<Transaction> foundById = transactionRepository.findById(saved.getId());
        Optional<Transaction> foundByKey = transactionRepository.findByIdempotencyKey(idempotencyKey);
        boolean existsByKey = transactionRepository.existsByIdempotencyKey(idempotencyKey);

        // then
        assertThat(existsByKey).isTrue();
        assertThat(foundById).isPresent();
        assertThat(foundByKey).isPresent();

        Transaction retrieved = foundByKey.get();
        assertThat(retrieved.getId()).isEqualTo(saved.getId());
        assertThat(retrieved.getReferenceNumber()).isEqualTo(refNumber);
        assertThat(retrieved.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(retrieved.getSenderWalletId()).isNull();
        assertThat(retrieved.getRecipientWalletId()).isEqualTo(recipientWallet.getId());
        assertThat(retrieved.getAmount()).isEqualByComparingTo(new BigDecimal("500.2500"));
        assertThat(retrieved.getType()).isEqualTo(TransactionType.TOP_UP);
        assertThat(retrieved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(retrieved.getDescription()).isEqualTo("Initial account top-up");
        assertThat(retrieved.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should enforce unique constraint on idempotency_key at database level")
    void shouldEnforceUniqueConstraintOnIdempotencyKey() {
        // given
        Instant now = Instant.now(fixedClock);
        Wallet wallet = walletRepository.saveAndFlush(Wallet.createDefault(UUID.randomUUID(), now));
        UUID duplicateIdempotencyKey = UUID.randomUUID();

        Transaction firstTx = Transaction.createTopUp(
                Transaction.formatReferenceNumber(transactionRepository.getNextReferenceSequenceValue()),
                duplicateIdempotencyKey,
                wallet.getId(),
                new BigDecimal("100.0000"),
                "First top-up",
                now
        );
        Transaction secondTx = Transaction.createTopUp(
                Transaction.formatReferenceNumber(transactionRepository.getNextReferenceSequenceValue()),
                duplicateIdempotencyKey,
                wallet.getId(),
                new BigDecimal("200.0000"),
                "Duplicate key top-up",
                now
        );

        transactionRepository.saveAndFlush(firstTx);

        // when & then
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(secondTx))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce unique constraint on reference_number at database level")
    void shouldEnforceUniqueConstraintOnReferenceNumber() {
        // given
        Instant now = Instant.now(fixedClock);
        Wallet wallet = walletRepository.saveAndFlush(Wallet.createDefault(UUID.randomUUID(), now));
        String duplicateReference = Transaction.formatReferenceNumber(
                transactionRepository.getNextReferenceSequenceValue()
        );

        Transaction firstTx = Transaction.createTopUp(
                duplicateReference,
                UUID.randomUUID(),
                wallet.getId(),
                new BigDecimal("100.0000"),
                "First tx",
                now
        );
        Transaction secondTx = Transaction.createTopUp(
                duplicateReference,
                UUID.randomUUID(),
                wallet.getId(),
                new BigDecimal("100.0000"),
                "Second tx with same ref",
                now
        );

        transactionRepository.saveAndFlush(firstTx);

        // when & then
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(secondTx))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce foreign key constraint when recipient_wallet_id does not exist")
    void shouldEnforceForeignKeyConstraintOnRecipientWallet() {
        // given
        Instant now = Instant.now(fixedClock);
        UUID nonExistentWalletId = UUID.randomUUID();
        String refNumber = Transaction.formatReferenceNumber(transactionRepository.getNextReferenceSequenceValue());

        Transaction orphanTopUp = Transaction.createTopUp(
                refNumber,
                UUID.randomUUID(),
                nonExistentWalletId,
                new BigDecimal("50.0000"),
                "Orphan top-up",
                now
        );

        // when & then
        assertThatThrownBy(() -> transactionRepository.saveAndFlush(orphanTopUp))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should retrieve paginated transactions where wallet is either sender or recipient")
    void shouldFindPaginatedTransactionsByWalletId() {
        // given
        Instant t1 = Instant.now(fixedClock);
        Instant t2 = t1.plusSeconds(60);
        Instant t3 = t1.plusSeconds(120);

        Wallet walletA = walletRepository.saveAndFlush(Wallet.createDefault(UUID.randomUUID(), t1));
        Wallet walletB = walletRepository.saveAndFlush(Wallet.createDefault(UUID.randomUUID(), t1));
        Wallet walletC = walletRepository.saveAndFlush(Wallet.createDefault(UUID.randomUUID(), t1));

        Transaction topUpA = Transaction.createTopUp(
                Transaction.formatReferenceNumber(transactionRepository.getNextReferenceSequenceValue()),
                UUID.randomUUID(),
                walletA.getId(),
                new BigDecimal("300.0000"),
                "Top up A",
                t1
        );
        Transaction transferAToB = Transaction.createTransfer(
                Transaction.formatReferenceNumber(transactionRepository.getNextReferenceSequenceValue()),
                UUID.randomUUID(),
                walletA.getId(),
                walletB.getId(),
                new BigDecimal("100.0000"),
                "Transfer A -> B",
                t2
        );
        Transaction transferBToC = Transaction.createTransfer(
                Transaction.formatReferenceNumber(transactionRepository.getNextReferenceSequenceValue()),
                UUID.randomUUID(),
                walletB.getId(),
                walletC.getId(),
                new BigDecimal("50.0000"),
                "Transfer B -> C (unrelated to A)",
                t3
        );

        transactionRepository.saveAndFlush(topUpA);
        transactionRepository.saveAndFlush(transferAToB);
        transactionRepository.saveAndFlush(transferBToC);

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        // when
        Page<Transaction> walletATransactions = transactionRepository.findAllByWalletId(walletA.getId(), pageRequest);

        // then
        assertThat(walletATransactions.getTotalElements()).isEqualTo(2);
        assertThat(walletATransactions.getContent())
                .extracting(Transaction::getId)
                .containsExactly(transferAToB.getId(), topUpA.getId());
    }
}