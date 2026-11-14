package com.nn.walletapp_api.transaction.service;

import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.transaction.api.mapper.TransactionMapper;
import com.nn.walletapp_api.transaction.api.request.TopUpRequest;
import com.nn.walletapp_api.transaction.api.request.TransferRequest;
import com.nn.walletapp_api.transaction.api.response.TransactionResponse;
import com.nn.walletapp_api.transaction.domain.Transaction;
import com.nn.walletapp_api.transaction.domain.TransactionStatus;
import com.nn.walletapp_api.transaction.domain.TransactionType;
import com.nn.walletapp_api.transaction.exception.DuplicateTransactionException;
import com.nn.walletapp_api.transaction.exception.SelfTransferException;
import com.nn.walletapp_api.transaction.repository.TransactionRepository;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.exception.InsufficientFundsException;
import com.nn.walletapp_api.wallet.exception.WalletLockedException;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.exception.WalletOwnershipException;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.inOrder;
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

    @Test
    @DisplayName("Should execute P2P transfer and acquire locks in ascending UUID order when sender UUID > recipient UUID")
    void shouldExecuteTransferAndAcquireLocksInAscendingOrderWhenSenderIdGreaterThanRecipientId() {
        // given
        UUID senderOwnerId = UUID.randomUUID();
        UUID recipientOwnerId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(senderOwnerId, AccessLevel.STANDARD);

        UUID smallerRecipientWalletId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID largerSenderWalletId = UUID.fromString("20000000-0000-0000-0000-000000000002");

        Wallet senderWallet = createWalletWithId(largerSenderWalletId, senderOwnerId, new BigDecimal("500.0000"));
        Wallet recipientWallet = createWalletWithId(smallerRecipientWalletId, recipientOwnerId, new BigDecimal("100.0000"));

        UUID idempotencyKey = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                largerSenderWalletId,
                smallerRecipientWalletId,
                new BigDecimal("175.2500"),
                "Invoice payment"
        );

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(smallerRecipientWalletId)).willReturn(Optional.of(recipientWallet));
        given(walletRepository.findByIdWithLock(largerSenderWalletId)).willReturn(Optional.of(senderWallet));
        given(transactionRepository.getNextReferenceSequenceValue()).willReturn(42L);
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(transactionMapper.toResponse(any(Transaction.class)))
                .willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        // when
        TransactionResponse response = transactionService.transfer(idempotencyKey, request, requester);

        // then
        InOrder inOrder = inOrder(walletRepository);
        then(walletRepository).should(inOrder).findByIdWithLock(smallerRecipientWalletId);
        then(walletRepository).should(inOrder).findByIdWithLock(largerSenderWalletId);

        assertThat(senderWallet.getBalance()).isEqualByComparingTo(new BigDecimal("324.7500"));
        assertThat(recipientWallet.getBalance()).isEqualByComparingTo(new BigDecimal("275.2500"));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        then(transactionRepository).should().save(txCaptor.capture());

        Transaction capturedTx = txCaptor.getValue();
        assertThat(capturedTx.getReferenceNumber()).isEqualTo("TRX0000042");
        assertThat(capturedTx.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(capturedTx.getSenderWalletId()).isEqualTo(largerSenderWalletId);
        assertThat(capturedTx.getRecipientWalletId()).isEqualTo(smallerRecipientWalletId);
        assertThat(capturedTx.getAmount()).isEqualByComparingTo(new BigDecimal("175.2500"));
        assertThat(capturedTx.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(capturedTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(capturedTx.getDescription()).isEqualTo("Invoice payment");
        assertThat(capturedTx.getCreatedAt()).isEqualTo(FIXED_INSTANT);

        assertThat(response.referenceNumber()).isEqualTo("TRX0000042");
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        then(walletRepository).should(never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Should execute P2P transfer and acquire locks in ascending UUID order when sender UUID < recipient UUID")
    void shouldExecuteTransferAndAcquireLocksInAscendingOrderWhenSenderIdSmallerThanRecipientId() {
        // given
        UUID senderOwnerId = UUID.randomUUID();
        UUID recipientOwnerId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(senderOwnerId, AccessLevel.STANDARD);

        UUID smallerSenderWalletId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID largerRecipientWalletId = UUID.fromString("20000000-0000-0000-0000-000000000002");

        Wallet senderWallet = createWalletWithId(smallerSenderWalletId, senderOwnerId, new BigDecimal("300.0000"));
        Wallet recipientWallet = createWalletWithId(largerRecipientWalletId, recipientOwnerId, new BigDecimal("50.0000"));

        UUID idempotencyKey = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                smallerSenderWalletId,
                largerRecipientWalletId,
                new BigDecimal("100.0000"),
                "Dinner split"
        );

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(smallerSenderWalletId)).willReturn(Optional.of(senderWallet));
        given(walletRepository.findByIdWithLock(largerRecipientWalletId)).willReturn(Optional.of(recipientWallet));
        given(transactionRepository.getNextReferenceSequenceValue()).willReturn(43L);
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(transactionMapper.toResponse(any(Transaction.class)))
                .willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        // when
        TransactionResponse response = transactionService.transfer(idempotencyKey, request, requester);

        // then
        InOrder inOrder = inOrder(walletRepository);
        then(walletRepository).should(inOrder).findByIdWithLock(smallerSenderWalletId);
        then(walletRepository).should(inOrder).findByIdWithLock(largerRecipientWalletId);

        assertThat(senderWallet.getBalance()).isEqualByComparingTo(new BigDecimal("200.0000"));
        assertThat(recipientWallet.getBalance()).isEqualByComparingTo(new BigDecimal("150.0000"));
        assertThat(response.referenceNumber()).isEqualTo("TRX0000043");
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should throw SelfTransferException immediately before DB queries when sender equals recipient (OWASP API6)")
    void shouldThrowSelfTransferExceptionWhenSenderEqualsRecipient() {
        // given
        UUID sameWalletId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(UUID.randomUUID(), AccessLevel.STANDARD);
        TransferRequest request = new TransferRequest(
                sameWalletId,
                sameWalletId,
                new BigDecimal("50.0000"),
                "Self transfer"
        );

        // when & then
        assertThatThrownBy(() -> transactionService.transfer(idempotencyKey, request, requester))
                .isInstanceOf(SelfTransferException.class)
                .hasMessageContaining(sameWalletId.toString());

        then(transactionRepository).shouldHaveNoInteractions();
        then(walletRepository).shouldHaveNoInteractions();
        then(transactionMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when sender balance is lower than transfer amount")
    void shouldThrowInsufficientFundsExceptionWhenBalanceTooLow() {
        // given
        UUID senderOwnerId = UUID.randomUUID();
        UUID recipientOwnerId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(senderOwnerId, AccessLevel.STANDARD);

        Wallet senderWallet = Wallet.createDefault(senderOwnerId, FIXED_INSTANT);
        senderWallet.credit(new BigDecimal("40.0000"));
        Wallet recipientWallet = Wallet.createDefault(recipientOwnerId, FIXED_INSTANT);

        UUID idempotencyKey = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                senderWallet.getId(),
                recipientWallet.getId(),
                new BigDecimal("100.0000"),
                "Transfer exceeding balance"
        );

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(senderWallet.getId())).willReturn(Optional.of(senderWallet));
        given(walletRepository.findByIdWithLock(recipientWallet.getId())).willReturn(Optional.of(recipientWallet));
        given(transactionRepository.getNextReferenceSequenceValue()).willReturn(50L);

        // when & then
        assertThatThrownBy(() -> transactionService.transfer(idempotencyKey, request, requester))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(senderWallet.getBalance()).isEqualByComparingTo(new BigDecimal("40.0000"));
        assertThat(recipientWallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        then(transactionRepository).should(never()).save(any(Transaction.class));
        then(transactionMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw WalletOwnershipException when user attempts transfer from another user's wallet (IDOR)")
    void shouldThrowWalletOwnershipExceptionOnTransferFromForeignWallet() {
        // given
        UUID victimOwnerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        RequesterContext attackerContext = new RequesterContext(attackerId, AccessLevel.STANDARD);

        Wallet victimSenderWallet = Wallet.createDefault(victimOwnerId, FIXED_INSTANT);
        victimSenderWallet.credit(new BigDecimal("500.0000"));
        Wallet attackerRecipientWallet = Wallet.createDefault(attackerId, FIXED_INSTANT);

        UUID idempotencyKey = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                victimSenderWallet.getId(),
                attackerRecipientWallet.getId(),
                new BigDecimal("200.0000"),
                "Unauthorized transfer"
        );

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(victimSenderWallet.getId())).willReturn(Optional.of(victimSenderWallet));
        given(walletRepository.findByIdWithLock(attackerRecipientWallet.getId())).willReturn(Optional.of(attackerRecipientWallet));

        // when & then
        assertThatThrownBy(() -> transactionService.transfer(idempotencyKey, request, attackerContext))
                .isInstanceOf(WalletOwnershipException.class)
                .hasMessageContaining("Security violation");

        assertThat(victimSenderWallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.0000"));
        assertThat(attackerRecipientWallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        then(transactionRepository).should(never()).getNextReferenceSequenceValue();
        then(transactionRepository).should(never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should throw WalletLockedException when recipient wallet is locked")
    void shouldThrowWalletLockedExceptionWhenRecipientWalletIsLocked() {
        // given
        UUID senderOwnerId = UUID.randomUUID();
        UUID recipientOwnerId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(senderOwnerId, AccessLevel.STANDARD);

        Wallet senderWallet = Wallet.createDefault(senderOwnerId, FIXED_INSTANT);
        senderWallet.credit(new BigDecimal("200.0000"));
        Wallet lockedRecipientWallet = Wallet.createDefault(recipientOwnerId, FIXED_INSTANT);
        lockedRecipientWallet.lock();

        UUID idempotencyKey = UUID.randomUUID();
        TransferRequest request = new TransferRequest(
                senderWallet.getId(),
                lockedRecipientWallet.getId(),
                new BigDecimal("50.0000"),
                "Transfer to locked wallet"
        );

        given(transactionRepository.existsByIdempotencyKey(idempotencyKey)).willReturn(false);
        given(walletRepository.findByIdWithLock(senderWallet.getId())).willReturn(Optional.of(senderWallet));
        given(walletRepository.findByIdWithLock(lockedRecipientWallet.getId())).willReturn(Optional.of(lockedRecipientWallet));
        given(transactionRepository.getNextReferenceSequenceValue()).willReturn(60L);

        // when & then
        assertThatThrownBy(() -> transactionService.transfer(idempotencyKey, request, requester))
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining("in LOCKED state");

        then(transactionRepository).should(never()).save(any(Transaction.class));
        then(transactionMapper).shouldHaveNoInteractions();
    }

    private Wallet createWalletWithId(UUID walletId, UUID ownerSub, BigDecimal initialBalance) {
        Wallet wallet = Wallet.createDefault(ownerSub, FIXED_INSTANT);
        ReflectionTestUtils.setField(wallet, "id", walletId);
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            wallet.credit(initialBalance);
        }
        return wallet;
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