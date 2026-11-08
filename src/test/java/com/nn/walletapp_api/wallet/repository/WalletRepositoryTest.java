package com.nn.walletapp_api.wallet.repository;

import com.nn.walletapp_api.support.BaseIntegrationTest;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.domain.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WalletRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist and retrieve wallet with precise scale and initial version")
    void shouldPersistAndRetrieveWallet() {
        // given
        UUID ownerSub = UUID.randomUUID();
        Instant now = Instant.now(fixedClock);
        Wallet wallet = Wallet.createNew(ownerSub, "Savings Wallet", now);

        // when
        Wallet savedWallet = walletRepository.saveAndFlush(wallet);
        Optional<Wallet> foundWallet = walletRepository.findById(savedWallet.getId());

        // then
        assertThat(foundWallet).isPresent();
        Wallet retrieved = foundWallet.get();
        assertThat(retrieved.getId()).isEqualTo(savedWallet.getId());
        assertThat(retrieved.getOwnerSub()).isEqualTo(ownerSub);
        assertThat(retrieved.getLabel()).isEqualTo("Savings Wallet");
        assertThat(retrieved.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(retrieved.getCurrency()).isEqualTo("PLN");
        assertThat(retrieved.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(retrieved.getVersion()).isNotNull().isZero();
        assertThat(retrieved.getCreatedAt()).isNotNull();
        assertThat(retrieved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find wallet by ownerSub when wallet exists")
    void shouldFindWalletByOwnerSub() {
        // given
        UUID ownerSub = UUID.randomUUID();
        Wallet wallet = Wallet.createDefault(ownerSub, Instant.now(fixedClock));
        walletRepository.saveAndFlush(wallet);

        // when
        Optional<Wallet> foundWallet = walletRepository.findByOwnerSub(ownerSub);

        // then
        assertThat(foundWallet).isPresent();
        assertThat(foundWallet.get().getOwnerSub()).isEqualTo(ownerSub);
    }

    @Test
    @DisplayName("Should return empty optional when wallet not found by ownerSub")
    void shouldReturnEmptyWhenWalletNotFoundByOwnerSub() {
        // given
        UUID nonExistentOwnerSub = UUID.randomUUID();

        // when
        Optional<Wallet> foundWallet = walletRepository.findByOwnerSub(nonExistentOwnerSub);

        // then
        assertThat(foundWallet).isEmpty();
    }

    @Test
    @DisplayName("Should check if wallet exists by ownerSub")
    void shouldCheckIfWalletExistsByOwnerSub() {
        // given
        UUID ownerSub = UUID.randomUUID();
        walletRepository.saveAndFlush(Wallet.createDefault(ownerSub, Instant.now(fixedClock)));

        // when / then
        assertThat(walletRepository.existsByOwnerSub(ownerSub)).isTrue();
        assertThat(walletRepository.existsByOwnerSub(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Should enforce unique constraint on ownerSub at database level")
    void shouldEnforceUniqueConstraintOnOwnerSub() {
        // given
        UUID duplicateOwnerSub = UUID.randomUUID();
        Wallet firstWallet = Wallet.createDefault(duplicateOwnerSub, Instant.now(fixedClock));
        Wallet secondWallet = Wallet.createDefault(duplicateOwnerSub, Instant.now(fixedClock));

        walletRepository.saveAndFlush(firstWallet);

        // when & then
        assertThatThrownBy(() -> walletRepository.saveAndFlush(secondWallet))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should increment version on entity mutation via Hibernate dirty checking")
    void shouldIncrementVersionOnEntityMutation() {
        // given
        UUID ownerSub = UUID.randomUUID();
        Wallet initialWallet = walletRepository.saveAndFlush(
                Wallet.createDefault(ownerSub, Instant.now(fixedClock))
        );
        UUID walletId = initialWallet.getId();
        Long initialVersion = initialWallet.getVersion();

        // when
        transactionTemplate.executeWithoutResult(status -> {
            Wallet managedWallet = walletRepository.findById(walletId).orElseThrow();
            managedWallet.credit(new BigDecimal("150.0000"));
        });

        // then
        Wallet updatedWallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(updatedWallet.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("150.0000"));
    }

    @Test
    @DisplayName("Should successfully acquire pessimistic write lock within transaction")
    void shouldAcquirePessimisticWriteLockWithinTransaction() {
        // given
        UUID ownerSub = UUID.randomUUID();
        Wallet wallet = walletRepository.saveAndFlush(
                Wallet.createDefault(ownerSub, Instant.now(fixedClock))
        );

        // when
        Optional<Wallet> lockedWallet = transactionTemplate.execute(status ->
                walletRepository.findByIdWithLock(wallet.getId())
        );

        // then
        assertThat(lockedWallet).isPresent();
        assertThat(lockedWallet.get().getId()).isEqualTo(wallet.getId());
        assertThat(lockedWallet.get().getOwnerSub()).isEqualTo(ownerSub);
    }
}
