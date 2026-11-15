package com.nn.walletapp_api.transaction.service;

import com.nn.walletapp_api.shared.api.response.PageResponse;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.transaction.api.mapper.TransactionMapper;
import com.nn.walletapp_api.transaction.api.request.TopUpRequest;
import com.nn.walletapp_api.transaction.api.request.TransferRequest;
import com.nn.walletapp_api.transaction.api.response.TransactionResponse;
import com.nn.walletapp_api.transaction.domain.Transaction;
import com.nn.walletapp_api.transaction.event.TransactionCompletedEvent;
import com.nn.walletapp_api.transaction.exception.DuplicateTransactionException;
import com.nn.walletapp_api.transaction.exception.SelfTransferException;
import com.nn.walletapp_api.transaction.exception.TransactionNotFoundException;
import com.nn.walletapp_api.transaction.exception.TransactionOwnershipException;
import com.nn.walletapp_api.transaction.repository.TransactionRepository;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionMapper transactionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public TransactionResponse topUp(UUID idempotencyKey, TopUpRequest request, RequesterContext requester) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(requester, "requester must not be null");

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new DuplicateTransactionException(idempotencyKey);
        }

        Wallet wallet = walletRepository.findByIdWithLock(request.walletId())
                .orElseThrow(() -> new WalletNotFoundException(request.walletId()));

        wallet.verifyOwnership(requester.userId());

        long sequenceNumber = transactionRepository.getNextReferenceSequenceValue();
        String referenceNumber = Transaction.formatReferenceNumber(sequenceNumber);

        Transaction transaction = Transaction.createTopUp(
                referenceNumber,
                idempotencyKey,
                wallet.getId(),
                request.amount(),
                request.description(),
                Instant.now(clock)
        );

        wallet.credit(request.amount());
        transaction.markCompleted();

        Transaction savedTransaction = transactionRepository.save(transaction);
        eventPublisher.publishEvent(TransactionCompletedEvent.from(savedTransaction));

        log.info("Completed top-up transaction {} for wallet {} with amount {}",
                savedTransaction.getReferenceNumber(), wallet.getId(), savedTransaction.getAmount());

        return transactionMapper.toResponse(savedTransaction);
    }

    @Transactional
    public TransactionResponse transfer(UUID idempotencyKey, TransferRequest request, RequesterContext requester) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(requester, "requester must not be null");

        UUID senderId = Objects.requireNonNull(request.senderWalletId(), "senderWalletId must not be null");
        UUID recipientId = Optional.of(Objects.requireNonNull(request.recipientWalletId(), "recipientWalletId must not be null"))
                .filter(targetId -> !senderId.equals(targetId))
                .orElseThrow(() -> new SelfTransferException(senderId));

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new DuplicateTransactionException(idempotencyKey);
        }

        boolean senderIsFirst = senderId.compareTo(recipientId) < 0;
        UUID firstLockId = senderIsFirst ? senderId : recipientId;
        UUID secondLockId = senderIsFirst ? recipientId : senderId;

        Wallet firstLockedWallet = walletRepository.findByIdWithLock(firstLockId)
                .orElseThrow(() -> new WalletNotFoundException(firstLockId));
        Wallet secondLockedWallet = walletRepository.findByIdWithLock(secondLockId)
                .orElseThrow(() -> new WalletNotFoundException(secondLockId));

        Wallet senderWallet = senderIsFirst ? firstLockedWallet : secondLockedWallet;
        Wallet recipientWallet = senderIsFirst ? secondLockedWallet : firstLockedWallet;

        senderWallet.verifyOwnership(requester.userId());

        long sequenceNumber = transactionRepository.getNextReferenceSequenceValue();
        String referenceNumber = Transaction.formatReferenceNumber(sequenceNumber);

        Transaction transaction = Transaction.createTransfer(
                referenceNumber,
                idempotencyKey,
                senderWallet.getId(),
                recipientWallet.getId(),
                request.amount(),
                request.description(),
                Instant.now(clock)
        );

        senderWallet.debit(request.amount());
        recipientWallet.credit(request.amount());
        transaction.markCompleted();

        Transaction savedTransaction = transactionRepository.save(transaction);
        eventPublisher.publishEvent(TransactionCompletedEvent.from(savedTransaction));

        log.info("Completed P2P transfer {} from wallet {} to wallet {} with amount {}",
                savedTransaction.getReferenceNumber(),
                senderWallet.getId(),
                recipientWallet.getId(),
                savedTransaction.getAmount());

        return transactionMapper.toResponse(savedTransaction);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getUserTransactions(RequesterContext requester, Pageable pageable) {
        Objects.requireNonNull(requester, "requester must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");

        Page<TransactionResponse> mappedPage = walletRepository.findByOwnerSub(requester.userId())
                .map(wallet -> transactionRepository.findAllByWalletId(wallet.getId(), pageable))
                .orElseGet(() -> Page.empty(pageable))
                .map(transactionMapper::toResponse);

        return PageResponse.from(mappedPage);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID transactionId, RequesterContext requester) {
        Transaction transaction = getValidatedTransaction(transactionId, requester);
        return transactionMapper.toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public Transaction getValidatedTransaction(UUID transactionId, RequesterContext requester) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(requester, "requester must not be null");

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (!requester.isAdmin()) {
            Wallet requesterWallet = walletRepository.findByOwnerSub(requester.userId())
                    .orElseThrow(() -> new TransactionOwnershipException(
                            String.format("Security violation: requester %s does not own a participating wallet for transaction %s",
                                    requester.userId(), transactionId)
                    ));
            transaction.verifyParticipation(requesterWallet.getId());
        }

        return transaction;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAllTransactions(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable must not be null");

        Page<TransactionResponse> mappedPage = transactionRepository.findAll(pageable)
                .map(transactionMapper::toResponse);

        return PageResponse.from(mappedPage);
    }
}