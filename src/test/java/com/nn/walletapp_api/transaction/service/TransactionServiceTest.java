package com.nn.walletapp_api.transaction.service;

import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.transaction.api.mapper.TransactionMapper;
import com.nn.walletapp_api.transaction.api.request.TopUpRequest;
import com.nn.walletapp_api.transaction.api.response.TransactionResponse;
import com.nn.walletapp_api.transaction.domain.Transaction;
import com.nn.walletapp_api.transaction.domain.TransactionStatus;
import com.nn.walletapp_api.transaction.domain.TransactionType;
import com.nn.walletapp_api.transaction.exception.DuplicateTransactionException;
import com.nn.walletapp_api.transaction.repository.TransactionRepository;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.exception.WalletLockedException;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.exception.WalletOwnershipException;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-11T19:00:00Z");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionMapper transactionMapper;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        transactionService = new TransactionService(
                transactionRepository,
                walletRepository,
                transactionMapper,
                fixedClock
        );
    }

    @Test
    @DisplayName("Should execute top-up, credit locked wallet, transition transaction to COMPLETED and persist")
    void shouldExecuteTopUpSuccessfully() {
        // given
        UUID userId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(userId, AccessLevel.STANDARD);
        Wallet wallet = Wallet.createDefault(userId, FIXED_INSTANT);
        UUID walletId = wallet.getId();
        UUID idempotencyKey = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(walletId, new BigDecimal("250.5000"), "Salary deposit");

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(walletId)).willReturn(Optional.of(wallet));
        given(transactionRepository.getNextReferenceSequenceValue()).willReturn(7L);
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(transactionMapper.toResponse(any(Transaction.class)))
                .willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        // when
        TransactionResponse response = transactionService.topUp(idempotencyKey, request, requester);

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("250.5000"));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        then(transactionRepository).should().save(txCaptor.capture());

        Transaction capturedTx = txCaptor.getValue();
        assertThat(capturedTx.getReferenceNumber()).isEqualTo("TRX0000007");
        assertThat(capturedTx.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(capturedTx.getSenderWalletId()).isNull();
        assertThat(capturedTx.getRecipientWalletId()).isEqualTo(walletId);
        assertThat(capturedTx.getAmount()).isEqualByComparingTo(new BigDecimal("250.5000"));
        assertThat(capturedTx.getType()).isEqualTo(TransactionType.TOP_UP);
        assertThat(capturedTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(capturedTx.getDescription()).isEqualTo("Salary deposit");
        assertThat(capturedTx.getCreatedAt()).isEqualTo(FIXED_INSTANT);

        assertThat(response.referenceNumber()).isEqualTo("TRX0000007");
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("250.5000"));

        then(walletRepository).should(never()).save(any(Wallet.class));
        then(transactionMapper).should().toResponse(capturedTx);
    }

    @Test
    @DisplayName("Should throw DuplicateTransactionException when idempotency key already exists")
    void shouldThrowDuplicateTransactionExceptionWhenIdempotencyKeyExists() {
        // given
        UUID idempotencyKey = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(userId, AccessLevel.STANDARD);
        TopUpRequest request = new TopUpRequest(UUID.randomUUID(), new BigDecimal("100.0000"), "Top-up");

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> transactionService.topUp(idempotencyKey, request, requester))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining(idempotencyKey.toString());

        then(walletRepository).shouldHaveNoInteractions();
        then(transactionRepository).should(never()).save(any(Transaction.class));
        then(transactionMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw WalletNotFoundException when target wallet does not exist")
    void shouldThrowWalletNotFoundExceptionWhenWalletDoesNotExist() {
        // given
        UUID idempotencyKey = UUID.randomUUID();
        UUID nonExistentWalletId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(UUID.randomUUID(), AccessLevel.STANDARD);
        TopUpRequest request = new TopUpRequest(nonExistentWalletId, new BigDecimal("100.0000"), "Top-up");

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(nonExistentWalletId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> transactionService.topUp(idempotencyKey, request, requester))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(nonExistentWalletId.toString());

        then(transactionRepository).should(never()).getNextReferenceSequenceValue();
        then(transactionRepository).should(never()).save(any(Transaction.class));
        then(transactionMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw WalletOwnershipException when user attempts to top up another user's wallet (IDOR)")
    void shouldThrowWalletOwnershipExceptionOnIdorAttempt() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        RequesterContext attackerContext = new RequesterContext(attackerId, AccessLevel.STANDARD);
        Wallet targetWallet = Wallet.createDefault(ownerId, FIXED_INSTANT);
        UUID walletId = targetWallet.getId();
        UUID idempotencyKey = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(walletId, new BigDecimal("100.0000"), "Unauthorized top-up");

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(walletId)).willReturn(Optional.of(targetWallet));

        // when & then
        assertThatThrownBy(() -> transactionService.topUp(idempotencyKey, request, attackerContext))
                .isInstanceOf(WalletOwnershipException.class)
                .hasMessageContaining("Security violation");

        assertThat(targetWallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        then(transactionRepository).should(never()).getNextReferenceSequenceValue();
        then(transactionRepository).should(never()).save(any(Transaction.class));
        then(transactionMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw WalletLockedException when attempting to top up a locked wallet")
    void shouldThrowWalletLockedExceptionWhenWalletIsLocked() {
        // given
        UUID userId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(userId, AccessLevel.STANDARD);
        Wallet lockedWallet = Wallet.createDefault(userId, FIXED_INSTANT);
        lockedWallet.lock();
        UUID walletId = lockedWallet.getId();
        UUID idempotencyKey = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(walletId, new BigDecimal("100.0000"), "Top-up on locked wallet");

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(walletId)).willReturn(Optional.of(lockedWallet));
        given(transactionRepository.getNextReferenceSequenceValue()).willReturn(15L);

        // when & then
        assertThatThrownBy(() -> transactionService.topUp(idempotencyKey, request, requester))
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining("in LOCKED state");

        assertThat(lockedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        then(transactionRepository).should(never()).save(any(Transaction.class));
        then(transactionMapper).shouldHaveNoInteractions();
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReferenceNumber(),
                transaction.getIdempotencyKey(),
                transaction.getSenderWalletId(),
                transaction.getRecipientWalletId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}