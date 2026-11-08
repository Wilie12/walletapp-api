package com.nn.walletapp_api.wallet.service;

import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.wallet.api.mapper.WalletMapper;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.domain.WalletStatus;
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
public class WalletServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-09T00:00:00Z");

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletMapper walletMapper;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        walletService = new WalletService(walletRepository, walletMapper, fixedClock);
    }

    @Test
    @DisplayName("Should return existing wallet without provisioning when wallet already exists for user")
    void shouldReturnExistingWalletWhenAlreadyProvisioned() {
        // given
        UUID userId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(userId, AccessLevel.STANDARD);
        Wallet existingWallet = Wallet.createDefault(userId, FIXED_INSTANT);
        WalletResponse expectedResponse = toResponse(existingWallet);

        given(walletRepository.findByOwnerSub(userId)).willReturn(Optional.of(existingWallet));
        given(walletMapper.toResponse(existingWallet)).willReturn(expectedResponse);

        // when
        WalletResponse result = walletService.getOrProvisionMyWallet(requester);

        // then
        assertThat(result).isEqualTo(expectedResponse);
        then(walletRepository).should().findByOwnerSub(userId);
        then(walletRepository).should(never()).save(any(Wallet.class));
        then(walletMapper).should().toResponse(existingWallet);
    }

    @Test
    @DisplayName("Should lazily provision and persist new default wallet when none exists for user")
    void shouldLazilyProvisionNewWalletWhenNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(userId, AccessLevel.STANDARD);
        Wallet savedWallet = Wallet.createDefault(userId, FIXED_INSTANT);
        WalletResponse expectedResponse = toResponse(savedWallet);

        given(walletRepository.findByOwnerSub(userId)).willReturn(Optional.empty());
        given(walletRepository.save(any(Wallet.class))).willReturn(savedWallet);
        given(walletMapper.toResponse(savedWallet)).willReturn(expectedResponse);

        // when
        WalletResponse result = walletService.getOrProvisionMyWallet(requester);

        // then
        assertThat(result).isEqualTo(expectedResponse);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        then(walletRepository).should().save(walletCaptor.capture());

        Wallet capturedWallet = walletCaptor.getValue();
        assertThat(capturedWallet.getOwnerSub()).isEqualTo(userId);
        assertThat(capturedWallet.getLabel()).isEqualTo("Default Wallet");
        assertThat(capturedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(capturedWallet.getCurrency()).isEqualTo("PLN");
        assertThat(capturedWallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(capturedWallet.getCreatedAt()).isEqualTo(FIXED_INSTANT);

        then(walletMapper).should().toResponse(savedWallet);
    }

    @Test
    @DisplayName("Should return wallet by ID when requester is the verified owner")
    void shouldReturnWalletByIdWhenRequesterIsOwner() {
        // given
        UUID ownerId = UUID.randomUUID();
        RequesterContext ownerContext = new RequesterContext(ownerId, AccessLevel.STANDARD);
        Wallet wallet = Wallet.createDefault(ownerId, FIXED_INSTANT);
        UUID walletId = wallet.getId();
        WalletResponse expectedResponse = toResponse(wallet);

        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));
        given(walletMapper.toResponse(wallet)).willReturn(expectedResponse);

        // when
        WalletResponse result = walletService.getWalletById(walletId, ownerContext);

        // then
        assertThat(result).isEqualTo(expectedResponse);
        then(walletRepository).should().findById(walletId);
        then(walletMapper).should().toResponse(wallet);
    }

    @Test
    @DisplayName("Should allow ADMIN to retrieve any user wallet bypassing ownership check")
    void shouldAllowAdminToRetrieveAnyWallet() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        RequesterContext adminContext = new RequesterContext(adminId, AccessLevel.ADMIN);
        Wallet wallet = Wallet.createDefault(ownerId, FIXED_INSTANT);
        UUID walletId = wallet.getId();
        WalletResponse expectedResponse = toResponse(wallet);

        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));
        given(walletMapper.toResponse(wallet)).willReturn(expectedResponse);

        // when
        WalletResponse result = walletService.getWalletById(walletId, adminContext);

        // then
        assertThat(result).isEqualTo(expectedResponse);
        then(walletRepository).should().findById(walletId);
        then(walletMapper).should().toResponse(wallet);
    }

    @Test
    @DisplayName("Should throw WalletOwnershipException when standard user attempts IDOR on another wallet")
    void shouldThrowWalletOwnershipExceptionOnIdorAttempt() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        RequesterContext attackerContext = new RequesterContext(attackerId, AccessLevel.STANDARD);
        Wallet targetWallet = Wallet.createDefault(ownerId, FIXED_INSTANT);
        UUID walletId = targetWallet.getId();

        given(walletRepository.findById(walletId)).willReturn(Optional.of(targetWallet));

        // when & then
        assertThatThrownBy(() -> walletService.getWalletById(walletId, attackerContext))
                .isInstanceOf(WalletOwnershipException.class)
                .hasMessageContaining("Security violation");

        then(walletMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw WalletNotFoundException when requested wallet ID does not exist")
    void shouldThrowWalletNotFoundExceptionWhenWalletMissing() {
        // given
        UUID nonExistentWalletId = UUID.randomUUID();
        RequesterContext requester = new RequesterContext(UUID.randomUUID(), AccessLevel.STANDARD);

        given(walletRepository.findById(nonExistentWalletId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> walletService.getWalletById(nonExistentWalletId, requester))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(nonExistentWalletId.toString());

        then(walletMapper).shouldHaveNoInteractions();
    }

    private WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerSub(),
                wallet.getLabel(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getStatus(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }
}