package com.nn.walletapp_api.wallet.service;

import com.nn.walletapp_api.shared.api.response.PageResponse;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.wallet.api.mapper.WalletMapper;
import com.nn.walletapp_api.wallet.api.request.WalletStatusUpdateRequest;
import com.nn.walletapp_api.wallet.api.request.WalletUpdateRequest;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.domain.WalletStatus;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
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
}