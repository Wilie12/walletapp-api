package com.nn.walletapp_api.wallet.domain;

import com.nn.walletapp_api.wallet.exception.InsufficientFundsException;
import com.nn.walletapp_api.wallet.exception.InvalidAmountException;
import com.nn.walletapp_api.wallet.exception.WalletLockedException;
import com.nn.walletapp_api.wallet.exception.WalletOwnershipException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Audited
@Getter
@ToString(of = {"id", "ownerSub", "currency", "status", "balance"})
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    public static final int BALANCE_SCALE = 4;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_EVEN;
    public static final String DEFAULT_CURRENCY = "PLN";
    private static final String DEFAULT_LABEL = "Default Wallet";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_sub", nullable = false, unique = true, updatable = false)
    private UUID ownerSub;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "balance", nullable = false, precision = 19, scale = BALANCE_SCALE)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Wallet createNew(UUID ownerSub, String label, Instant creationInstant) {
        Objects.requireNonNull(ownerSub, "ownerSub must not be null");
        Objects.requireNonNull(creationInstant, "creationInstant must not be null");

        String validatedLabel = Optional.ofNullable(label)
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .orElse(DEFAULT_LABEL);

        Wallet wallet = new Wallet();
        wallet.id = UUID.randomUUID();
        wallet.ownerSub = ownerSub;
        wallet.label = validatedLabel;
        wallet.balance = BigDecimal.ZERO.setScale(BALANCE_SCALE, DEFAULT_ROUNDING);
        wallet.currency = DEFAULT_CURRENCY;
        wallet.status = WalletStatus.ACTIVE;
        wallet.createdAt = creationInstant;
        wallet.updatedAt = creationInstant;
        return wallet;
    }

    public static Wallet createDefault(UUID ownerSub, Instant creationInstant) {
        return createNew(ownerSub, DEFAULT_LABEL, creationInstant);
    }

    public void credit(BigDecimal amount) {
        ensureActiveState("credit");
        BigDecimal validatedAmount = validatePositiveAmount(amount);
        this.balance = this.balance.add(validatedAmount);
    }

    public void debit(BigDecimal amount) {
        ensureActiveState("debit");
        BigDecimal validatedAmount = validatePositiveAmount(amount);

        if (this.balance.compareTo(validatedAmount) < 0) {
            throw new InsufficientFundsException(
                    String.format("Insufficient funds in wallet %s. Available balance: %s, requested debit: %s",
                            this.id, this.balance, validatedAmount)
            );
        }
        this.balance = this.balance.subtract(validatedAmount);
    }

    public void lock() {
        if (isLocked()) {
            throw new WalletLockedException("Wallet is already locked with ID: " + this.id);
        }
        this.status = WalletStatus.LOCKED;
    }

    public void unlock() {
        if (isActive()) {
            throw new IllegalStateException("Wallet is already active with ID: " + this.id);
        }
        this.status = WalletStatus.ACTIVE;
    }

    public void updateLabel(String newLabel) {
        ensureActiveState("update label");
        this.label = Optional.ofNullable(newLabel)
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Wallet label must not be null or blank"));
    }

    public void verifyOwnership(UUID requesterId) {
        Objects.requireNonNull(requesterId, "requesterId must not be null");
        if (!this.ownerSub.equals(requesterId)) {
            throw new WalletOwnershipException(
                    String.format("Security violation: requester %s is not the owner of wallet %s",
                            requesterId, this.id)
            );
        }
    }

    public boolean isActive() {
        return WalletStatus.ACTIVE.equals(this.status);
    }

    public boolean isLocked() {
        return WalletStatus.LOCKED.equals(this.status);
    }

    private void ensureActiveState(String operationName) {
        if (isLocked()) {
            throw new WalletLockedException(
                    String.format("Cannot execute %s: wallet %s is in LOCKED state", operationName, this.id)
            );
        }
    }

    private BigDecimal validatePositiveAmount(BigDecimal amount) {
        return Optional.ofNullable(amount)
                .filter(a -> a.compareTo(BigDecimal.ZERO) > 0)
                .map(a -> a.setScale(BALANCE_SCALE, DEFAULT_ROUNDING))
                .orElseThrow(() -> new InvalidAmountException("Transaction amount must be strictly positive and non-null"));
    }
}
