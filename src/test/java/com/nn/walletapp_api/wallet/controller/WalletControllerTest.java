package com.nn.walletapp_api.wallet.controller;

import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.support.BaseControllerTest;
import com.nn.walletapp_api.support.SecurityTestUtils;
import com.nn.walletapp_api.wallet.api.request.WalletUpdateRequest;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.WalletStatus;
import com.nn.walletapp_api.wallet.exception.WalletLockedException;
import com.nn.walletapp_api.wallet.exception.WalletNotFoundException;
import com.nn.walletapp_api.wallet.exception.WalletOwnershipException;
import com.nn.walletapp_api.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WalletController.class)
public class WalletControllerTest extends BaseControllerTest {

    @MockitoBean
    private WalletService walletService;

    @Test
    @DisplayName("GET /api/v1/wallets/me should return 200 OK with provisioned wallet for authenticated user")
    void shouldGetOrProvisionMyWallet() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        RequesterContext expectedContext = new RequesterContext(userId, AccessLevel.STANDARD);
        WalletResponse response = new WalletResponse(
                walletId,
                userId,
                "Default Wallet",
                new BigDecimal("0.0000"),
                "PLN",
                WalletStatus.ACTIVE,
                FIXED_INSTANT,
                FIXED_INSTANT
        );

        given(walletService.getOrProvisionMyWallet(expectedContext)).willReturn(response);

        // when / then
        mockMvc.perform(get("/api/v1/wallets/me")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(walletId.toString()))
                .andExpect(jsonPath("$.ownerSub").value(userId.toString()))
                .andExpect(jsonPath("$.label").value("Default Wallet"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        then(walletService).should().getOrProvisionMyWallet(expectedContext);
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id} should return 200 OK when requester is authorized owner")
    void shouldGetWalletByIdWhenOwner() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        RequesterContext expectedContext = new RequesterContext(userId, AccessLevel.STANDARD);
        WalletResponse response = new WalletResponse(
                walletId,
                userId,
                "Personal Wallet",
                new BigDecimal("250.5000"),
                "PLN",
                WalletStatus.ACTIVE,
                FIXED_INSTANT,
                FIXED_INSTANT
        );

        given(walletService.getWalletById(walletId, expectedContext)).willReturn(response);

        // when / then
        mockMvc.perform(get("/api/v1/wallets/{id}", walletId)
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(walletId.toString()))
                .andExpect(jsonPath("$.label").value("Personal Wallet"))
                .andExpect(jsonPath("$.balance").value(250.5));

        then(walletService).should().getWalletById(walletId, expectedContext);
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id} should return 403 Forbidden ProblemDetail on IDOR attempt")
    void shouldReturn403ForbiddenOnIdorAttempt() throws Exception {
        // given
        UUID attackerId = UUID.randomUUID();
        UUID targetWalletId = UUID.randomUUID();
        RequesterContext attackerContext = new RequesterContext(attackerId, AccessLevel.STANDARD);

        given(walletService.getWalletById(targetWalletId, attackerContext))
                .willThrow(new WalletOwnershipException(
                        "Security violation: requester " + attackerId + " is not the owner of wallet " + targetWalletId
                ));

        // when / then
        mockMvc.perform(get("/api/v1/wallets/{id}", targetWalletId)
                        .with(SecurityTestUtils.validJwt(attackerId, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Wallet Ownership Violation"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to access or modify this wallet"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/wallet-ownership-violation"))
                .andExpect(jsonPath("$.instance").value("/api/v1/wallets/" + targetWalletId))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id} should return 404 Not Found ProblemDetail when wallet does not exist")
    void shouldReturn404NotFoundWhenWalletDoesNotExist() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID nonExistentWalletId = UUID.randomUUID();
        RequesterContext requesterContext = new RequesterContext(userId, AccessLevel.STANDARD);

        given(walletService.getWalletById(nonExistentWalletId, requesterContext))
                .willThrow(new WalletNotFoundException(nonExistentWalletId));

        // when / then
        mockMvc.perform(get("/api/v1/wallets/{id}", nonExistentWalletId)
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Wallet Not Found"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/wallet-not-found"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));
    }

    @Test
    @DisplayName("PATCH /api/v1/wallets/{id} should update label and ignore injected Mass Assignment fields (BOPLA immunity)")
    void shouldUpdateLabelAndIgnoreMassAssignmentFields() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        RequesterContext expectedContext = new RequesterContext(userId, AccessLevel.STANDARD);

        String maliciousMassAssignmentPayload = """
                {
                    "label": "Updated Safe Label",
                    "balance": 999999.9999,
                    "status": "ACTIVE",
                    "currency": "EUR",
                    "ownerSub": "%s"
                }
                """.formatted(UUID.randomUUID());

        WalletResponse expectedResponse = new WalletResponse(
                walletId,
                userId,
                "Updated Safe Label",
                new BigDecimal("0.0000"),
                "PLN",
                WalletStatus.ACTIVE,
                FIXED_INSTANT,
                FIXED_INSTANT
        );

        ArgumentCaptor<WalletUpdateRequest> requestCaptor = ArgumentCaptor.forClass(WalletUpdateRequest.class);
        given(walletService.updateWalletLabel(eq(walletId), requestCaptor.capture(), eq(expectedContext)))
                .willReturn(expectedResponse);

        // when / then
        mockMvc.perform(patch("/api/v1/wallets/{id}", walletId)
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousMassAssignmentPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(walletId.toString()))
                .andExpect(jsonPath("$.label").value("Updated Safe Label"))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.currency").value("PLN"));

        WalletUpdateRequest capturedDto = requestCaptor.getValue();
        assertThat(capturedDto.label()).isEqualTo("Updated Safe Label");
    }

    @Test
    @DisplayName("PATCH /api/v1/wallets/{id} should return 400 Bad Request when label is blank or exceeds 100 chars")
    void shouldReturn400BadRequestWhenLabelIsBlank() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String invalidPayload = """
                {
                    "label": "   "
                }
                """;

        // when / then
        mockMvc.perform(patch("/api/v1/wallets/{id}", walletId)
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidFields.label").value("Wallet label must not be blank"));

        then(walletService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PATCH /api/v1/wallets/{id} should return 409 Conflict when wallet is in LOCKED state")
    void shouldReturn409ConflictWhenUpdatingLockedWallet() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        RequesterContext expectedContext = new RequesterContext(userId, AccessLevel.STANDARD);
        WalletUpdateRequest request = new WalletUpdateRequest("Valid Label");

        given(walletService.updateWalletLabel(walletId, request, expectedContext))
                .willThrow(new WalletLockedException("Cannot execute update label: wallet " + walletId + " is in LOCKED state"));

        String payload = """
                {
                    "label": "Valid Label"
                }
                """;

        // when / then
        mockMvc.perform(patch("/api/v1/wallets/{id}", walletId)
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Wallet Locked"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/wallet-locked"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));
    }
}