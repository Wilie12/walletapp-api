package com.nn.walletapp_api.wallet.service;

import com.nn.walletapp_api.shared.api.response.PageResponse;
import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.wallet.api.mapper.WalletMapper;
import com.nn.walletapp_api.wallet.api.request.WalletStatusUpdateRequest;
import com.nn.walletapp_api.wallet.api.request.WalletUpdateRequest;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.domain.WalletStatus;
import com.nn.walletapp_api.wallet.exception.WalletLockedException;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.exception.WalletOwnershipException;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

    @Mock
    private EntityManager entityManager;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        walletService = new WalletService(walletRepository, walletMapper, entityManager, fixedClock);
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

    @Test
    @DisplayName("Should update wallet label relying on Hibernate dirty checking when owner requests change")
    void shouldUpdateWalletLabelRelyingOnDirtyChecking() {
        // given
        UUID ownerId = UUID.randomUUID();
        RequesterContext ownerContext = new RequesterContext(ownerId, AccessLevel.STANDARD);
        Wallet wallet = Wallet.createDefault(ownerId, FIXED_INSTANT);
        UUID walletId = wallet.getId();
        WalletUpdateRequest updateRequest = new WalletUpdateRequest("Holiday Savings");

        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));
        given(walletMapper.toResponse(wallet)).willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        // when
        WalletResponse result = walletService.updateWalletLabel(walletId, updateRequest, ownerContext);

        // then
        assertThat(wallet.getLabel()).isEqualTo("Holiday Savings");
        assertThat(result.label()).isEqualTo("Holiday Savings");
        then(walletRepository).should().findById(walletId);
        then(walletRepository).should(never()).save(any(Wallet.class));
        then(walletMapper).should().toResponse(wallet);
    }

    @Test
    @DisplayName("Should throw WalletLockedException when attempting to update label on locked wallet")
    void shouldThrowWalletLockedExceptionWhenUpdatingLockedWalletLabel() {
        // given
        UUID ownerId = UUID.randomUUID();
        RequesterContext ownerContext = new RequesterContext(ownerId, AccessLevel.STANDARD);
        Wallet lockedWallet = Wallet.createDefault(ownerId, FIXED_INSTANT);
        lockedWallet.lock();
        UUID walletId = lockedWallet.getId();
        WalletUpdateRequest updateRequest = new WalletUpdateRequest("Attempted Label Change");

        given(walletRepository.findById(walletId)).willReturn(Optional.of(lockedWallet));

        // when & then
        assertThatThrownBy(() -> walletService.updateWalletLabel(walletId, updateRequest, ownerContext))
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining("in LOCKED state");

        then(walletRepository).should(never()).save(any(Wallet.class));
        then(walletMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw WalletOwnershipException when non-owner attempts to update wallet label")
    void shouldThrowWalletOwnershipExceptionWhenNonOwnerUpdatesLabel() {
        // given
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        RequesterContext attackerContext = new RequesterContext(otherUserId, AccessLevel.STANDARD);
        Wallet wallet = Wallet.createDefault(ownerId, FIXED_INSTANT);
        UUID walletId = wallet.getId();
        WalletUpdateRequest updateRequest = new WalletUpdateRequest("Hijacked Wallet");

        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));

        // when & then
        assertThatThrownBy(() -> walletService.updateWalletLabel(walletId, updateRequest, attackerContext))
                .isInstanceOf(WalletOwnershipException.class)
                .hasMessageContaining("Security violation");

        assertThat(wallet.getLabel()).isEqualTo("Default Wallet");
        then(walletRepository).should(never()).save(any(Wallet.class));
        then(walletMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should return paginated list of all wallets mapped to PageResponse")
    void shouldReturnPaginatedListOfAllWallets() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Wallet firstWallet = Wallet.createDefault(UUID.randomUUID(), FIXED_INSTANT);
        Wallet secondWallet = Wallet.createDefault(UUID.randomUUID(), FIXED_INSTANT);
        WalletResponse firstResponse = toResponse(firstWallet);
        WalletResponse secondResponse = toResponse(secondWallet);

        Page<Wallet> walletPage = new PageImpl<>(List.of(firstWallet, secondWallet), pageable, 2);

        given(walletRepository.findAll(pageable)).willReturn(walletPage);
        given(walletMapper.toResponse(firstWallet)).willReturn(firstResponse);
        given(walletMapper.toResponse(secondWallet)).willReturn(secondResponse);

        // when
        PageResponse<WalletResponse> result = walletService.getAllWallets(pageable);

        // then
        assertThat(result.content()).containsExactly(firstResponse, secondResponse);
        assertThat(result.pageNumber()).isZero();
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
        then(walletRepository).should().findAll(pageable);
    }

    @Test
    @DisplayName("Should lock active wallet and rely on Hibernate dirty checking")
    void shouldLockActiveWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), FIXED_INSTANT);
        UUID walletId = wallet.getId();
        WalletStatusUpdateRequest request = new WalletStatusUpdateRequest(WalletStatus.LOCKED);

        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));
        given(walletMapper.toResponse(wallet)).willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        // when
        WalletResponse result = walletService.updateWalletStatus(walletId, request);

        // then
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.LOCKED);
        assertThat(result.status()).isEqualTo(WalletStatus.LOCKED);
        then(walletRepository).should().findById(walletId);
        then(walletRepository).should(never()).save(any(Wallet.class));
        then(walletMapper).should().toResponse(wallet);
    }

    @Test
    @DisplayName("Should unlock locked wallet and rely on Hibernate dirty checking")
    void shouldUnlockLockedWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), FIXED_INSTANT);
        wallet.lock();
        UUID walletId = wallet.getId();
        WalletStatusUpdateRequest request = new WalletStatusUpdateRequest(WalletStatus.ACTIVE);

        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));
        given(walletMapper.toResponse(wallet)).willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        // when
        WalletResponse result = walletService.updateWalletStatus(walletId, request);

        // then
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(result.status()).isEqualTo(WalletStatus.ACTIVE);
        then(walletRepository).should().findById(walletId);
        then(walletRepository).should(never()).save(any(Wallet.class));
        then(walletMapper).should().toResponse(wallet);
    }

    @Test
    @DisplayName("Should throw WalletLockedException when locking an already locked wallet")
    void shouldThrowWalletLockedExceptionWhenLockingAlreadyLockedWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), FIXED_INSTANT);
        wallet.lock();
        UUID walletId = wallet.getId();
        WalletStatusUpdateRequest request = new WalletStatusUpdateRequest(WalletStatus.LOCKED);

        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));

        // when & then
        assertThatThrownBy(() -> walletService.updateWalletStatus(walletId, request))
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining("already locked");

        then(walletRepository).should(never()).save(any(Wallet.class));
        then(walletMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw IllegalStateException when unlocking an already active wallet")
    void shouldThrowIllegalStateExceptionWhenUnlockingAlreadyActiveWallet() {
        // given
        Wallet wallet = Wallet.createDefault(UUID.randomUUID(), FIXED_INSTANT);
        UUID walletId = wallet.getId();
        WalletStatusUpdateRequest request = new WalletStatusUpdateRequest(WalletStatus.ACTIVE);

        given(walletRepository.findById(walletId)).willReturn(Optional.of(wallet));

        // when & then
        assertThatThrownBy(() -> walletService.updateWalletStatus(walletId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active");

        then(walletRepository).should(never()).save(any(Wallet.class));
        then(walletMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw WalletNotFoundException when updating status of non-existent wallet")
    void shouldThrowWalletNotFoundExceptionWhenUpdatingStatusOfMissingWallet() {
        // given
        UUID nonExistentWalletId = UUID.randomUUID();
        WalletStatusUpdateRequest request = new WalletStatusUpdateRequest(WalletStatus.LOCKED);

        given(walletRepository.findById(nonExistentWalletId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> walletService.updateWalletStatus(nonExistentWalletId, request))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(nonExistentWalletId.toString());

        then(walletMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Should throw WalletNotFoundException when querying audit history for non-existent wallet")
    void shouldThrowWalletNotFoundExceptionWhenAuditWalletMissing() {
        // given
        UUID nonExistentWalletId = UUID.randomUUID();
        given(walletRepository.existsById(nonExistentWalletId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> walletService.getWalletAuditHistory(nonExistentWalletId))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(nonExistentWalletId.toString());

        then(entityManager).shouldHaveNoInteractions();
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