package com.nn.walletapp_api.wallet.service;

import com.nn.walletapp_api.shared.api.response.PageResponse;
import com.nn.walletapp_api.shared.audit.domain.CustomRevisionEntity;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.wallet.api.mapper.WalletMapper;
import com.nn.walletapp_api.wallet.api.request.WalletStatusUpdateRequest;
import com.nn.walletapp_api.wallet.api.request.WalletUpdateRequest;
import com.nn.walletapp_api.wallet.api.response.WalletAuditResponse;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.domain.WalletStatus;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public WalletResponse getOrProvisionMyWallet(RequesterContext requester) {
        Objects.requireNonNull(requester, "requester must not be null");

        Wallet wallet = walletRepository.findByOwnerSub(requester.userId())
                .orElseGet(() -> provisionNewWallet(requester.userId()));

        return walletMapper.toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletById(UUID walletId, RequesterContext requester) {
        Wallet wallet = getValidatedWallet(walletId, requester);
        return walletMapper.toResponse(wallet);
    }

    @Transactional
    public WalletResponse updateWalletLabel(UUID walletId, WalletUpdateRequest request, RequesterContext requester) {
        Objects.requireNonNull(request, "request must not be null");

        Wallet wallet = getValidatedWallet(walletId, requester);
        wallet.verifyOwnership(requester.userId());
        wallet.updateLabel(request.label());

        return walletMapper.toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletResponse> getAllWallets(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable must not be null");

        Page<WalletResponse> mappedPage = walletRepository.findAll(pageable)
                .map(walletMapper::toResponse);

        return PageResponse.from(mappedPage);
    }

    @Transactional
    public WalletResponse updateWalletStatus(UUID walletId, WalletStatusUpdateRequest request) {
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        if (WalletStatus.LOCKED.equals(request.status())) {
            wallet.lock();
        } else {
            wallet.unlock();
        }

        log.info("Updated wallet {} status to {}", walletId, wallet.getStatus());
        return walletMapper.toResponse(wallet);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<WalletAuditResponse> getWalletAuditHistory(UUID walletId) {
        Objects.requireNonNull(walletId, "walletId must not be null");

        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }

        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        List<Object[]> revisions = auditReader.createQuery()
                .forRevisionsOfEntity(Wallet.class, false, true)
                .add(AuditEntity.id().eq(walletId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        return revisions.stream()
                .map(this::mapToAuditResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Wallet getValidatedWallet(UUID walletId, RequesterContext requester) {
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(requester, "requester must not be null");

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        if (!requester.isAdmin()) {
            wallet.verifyOwnership(requester.userId());
        }

        return wallet;
    }

    private Wallet provisionNewWallet(UUID ownerSub) {
        log.info("Provisioning new default wallet for user sub: {}", ownerSub);
        Wallet newWallet = Wallet.createDefault(ownerSub, Instant.now(clock));
        return walletRepository.save(newWallet);
    }

    private WalletAuditResponse mapToAuditResponse(Object[] revisionTuple) {
        Wallet walletSnapshot = (Wallet) revisionTuple[0];
        CustomRevisionEntity revisionEntity = (CustomRevisionEntity) revisionTuple[1];
        RevisionType revisionType = (RevisionType) revisionTuple[2];

        return new WalletAuditResponse(
                revisionEntity.getId(),
                revisionType.name(),
                Instant.ofEpochMilli(revisionEntity.getTimestamp()),
                revisionEntity.getAuthorId(),
                walletSnapshot.getId(),
                walletSnapshot.getOwnerSub(),
                walletSnapshot.getLabel(),
                walletSnapshot.getBalance(),
                walletSnapshot.getCurrency(),
                walletSnapshot.getStatus()
        );
    }
}