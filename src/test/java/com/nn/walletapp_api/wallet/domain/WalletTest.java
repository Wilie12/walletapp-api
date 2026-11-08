package com.nn.walletapp_api.wallet.domain;

import com.nn.walletapp_api.wallet.exception.InsufficientFundsException;
import com.nn.walletapp_api.wallet.exception.InvalidAmountException;
import com.nn.walletapp_api.wallet.exception.WalletLockedException;
import com.nn.walletapp_api.wallet.exception.WalletOwnershipException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WalletTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-08T18:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("Should initialize active wallet with zero balance and default currency via factory method")
    void shouldInitializeActiveWalletWithFactoryMethod() {
        // given
        UUID ownerSub = UUID.randomUUID();
        Instant creationTime = Instant.now(fixedClock);

        // when
        Wallet wallet = Wallet.createNew(ownerSub, "Daily Savings", creationTime);

        // then
        assertThat(wallet.getId()).isNotNull();
        assertThat(wallet.getOwnerSub()).isEqualTo(ownerSub);
        assertThat(wallet.getLabel()).isEqualTo("Daily Savings");
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(wallet.getCurrency()).isEqualTo("PLN");
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.isActive()).isTrue();
        assertThat(wallet.isLocked()).isFalse();
        assertThat(wallet.getCreatedAt()).isEqualTo(creationTime);
        assertThat(wallet.getUpdatedAt()).isEqualTo(creationTime);
    }

    @Test
    @DisplayName("Should use default label when empty or null label provided to factory method")
    void shouldUseDefaultLabelWhenEmptyProvided() {
        // given
        UUID ownerSub = UUID.randomUUID();
        Instant creationTime = Instant.now(fixedClock);

        // when
        Wallet wallet = Wallet.createNew(ownerSub, "   ", creationTime);

        // then
        assertThat(wallet.getLabel()).isEqualTo("Default Wallet");
    }

    @Test
    @DisplayName("Should credit active wallet with positive amount and respect scale of 4")
    void shouldCreditActiveWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));
        BigDecimal creditAmount = new BigDecimal("150.25");

        // when
        wallet.credit(creditAmount);

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("150.2500"));
    }

    @Test
    @DisplayName("Should throw InvalidAmountException when crediting with zero or negative amount")
    void shouldRejectCreditWithZeroOrNegativeAmount() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));

        // when & then
        assertThatThrownBy(() -> wallet.credit(BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("strictly positive");

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("-10.00")))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("strictly positive");

        assertThatThrownBy(() -> wallet.credit(null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    @DisplayName("Should throw WalletLockedException when crediting locked wallet")
    void shouldRejectCreditOnLockedWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));
        wallet.lock();

        // when & then
        assertThatThrownBy(() -> wallet.credit(new BigDecimal("50.00")))
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining("in LOCKED state");
    }

    @Test
    @DisplayName("Should debit active wallet when sufficient funds are available")
    void shouldDebitActiveWalletWithSufficientFunds() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));
        wallet.credit(new BigDecimal("100.0000"));

        // when
        wallet.debit(new BigDecimal("40.5000"));

        // then
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("59.5000"));
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when debit exceeds available balance")
    void shouldRejectDebitWhenInsufficientFunds() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));
        wallet.credit(new BigDecimal("20.0000"));

        // when & then
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("25.0000")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    @DisplayName("Should throw InvalidAmountException when debiting with zero, negative or null amount")
    void shouldRejectDebitWithInvalidAmount() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));
        wallet.credit(new BigDecimal("100.0000"));

        // when & then
        assertThatThrownBy(() -> wallet.debit(BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("-5.00")))
                .isInstanceOf(InvalidAmountException.class);

        assertThatThrownBy(() -> wallet.debit(null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("Should throw WalletLockedException when debiting locked wallet")
    void shouldRejectDebitOnLockedWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));
        wallet.credit(new BigDecimal("100.0000"));
        wallet.lock();

        // when & then
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("50.0000")))
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining("in LOCKED state");
    }

    @Test
    @DisplayName("Should transition state between ACTIVE and LOCKED")
    void shouldTransitionStateCorrectly() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));

        // when
        wallet.lock();

        // then
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.LOCKED);
        assertThat(wallet.isLocked()).isTrue();

        // when
        wallet.unlock();

        // then
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should throw WalletLockedException when locking already locked wallet")
    void shouldRejectLockingWhenAlreadyLocked() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));
        wallet.lock();

        // when & then
        assertThatThrownBy(wallet::lock)
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining("already locked");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when unlocking already active wallet")
    void shouldRejectUnlockingWhenAlreadyActive() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));

        // when & then
        assertThatThrownBy(wallet::unlock)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active");
    }

    @Test
    @DisplayName("Should update label on active wallet and trim whitespace")
    void shouldUpdateLabelOnActiveWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));

        // when
        wallet.updateLabel("  Travel Expenses  ");

        // then
        assertThat(wallet.getLabel()).isEqualTo("Travel Expenses");
    }

    @Test
    @DisplayName("Should throw WalletLockedException when updating label on locked wallet")
    void shouldRejectLabelUpdateOnLockedWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));
        wallet.lock();

        // when & then
        assertThatThrownBy(() -> wallet.updateLabel("New Label"))
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining("in LOCKED state");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when updating label with null or blank value")
    void shouldRejectBlankLabelUpdate() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), Instant.now(fixedClock));

        // when & then
        assertThatThrownBy(() -> wallet.updateLabel(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> wallet.updateLabel("    "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should pass ownership verification when requester ID matches ownerSub")
    void shouldPassOwnershipVerification() {
        // given
        UUID ownerSub = UUID.randomUUID();
        Wallet wallet = Wallet.createDefault(ownerSub, Instant.now(fixedClock));

        // when & then (no exception thrown)
        wallet.verifyOwnership(ownerSub);
    }

    @Test
    @DisplayName("Should throw WalletOwnershipException when requester ID does not match ownerSub")
    void shouldFailOwnershipVerificationWhenNotOwner() {
        // given
        UUID ownerSub = UUID.randomUUID();
        UUID attackerSub = UUID.randomUUID();
        Wallet wallet = Wallet.createDefault(ownerSub, Instant.now(fixedClock));

        // when & then
        assertThatThrownBy(() -> wallet.verifyOwnership(attackerSub))
                .isInstanceOf(WalletOwnershipException.class)
                .hasMessageContaining("Security violation");
    }
}
