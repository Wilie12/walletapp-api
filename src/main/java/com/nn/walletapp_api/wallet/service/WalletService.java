package com.nn.walletapp_api.wallet.service;

import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.wallet.api.mapper.WalletMapper;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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