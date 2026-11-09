package com.nn.walletapp_api.wallet.controller;

import com.nn.walletapp_api.shared.api.response.PageResponse;
import com.nn.walletapp_api.support.BaseControllerTest;
import com.nn.walletapp_api.support.SecurityTestUtils;
import com.nn.walletapp_api.wallet.api.request.WalletStatusUpdateRequest;
import com.nn.walletapp_api.wallet.api.response.WalletAuditResponse;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.WalletStatus;
import com.nn.walletapp_api.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminWalletController.class)
public class AdminWalletControllerTest extends BaseControllerTest {

    @MockitoBean
    private WalletService walletService;

    @Test
    @DisplayName("GET /api/v1/admin/wallets should return 200 OK with paginated wallets for ROLE_ADMIN")
    void shouldReturnPaginatedWalletsForAdmin() throws Exception {
        // given
        UUID adminId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        WalletResponse walletResponse = new WalletResponse(
                walletId,
                ownerId,
                "Default Wallet",
                new BigDecimal("100.0000"),
                "PLN",
                WalletStatus.ACTIVE,
                FIXED_INSTANT,
                FIXED_INSTANT
        );
        PageResponse<WalletResponse> pageResponse = new PageResponse<>(
                List.of(walletResponse),
                0,
                20,
                1L,
                1,
                true,
                true
        );

        given(walletService.getAllWallets(any(Pageable.class))).willReturn(pageResponse);

        // when / then
        mockMvc.perform(get("/api/v1/admin/wallets")
                        .with(SecurityTestUtils.validJwt(adminId, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(walletId.toString()))
                .andExpect(jsonPath("$.content[0].ownerSub").value(ownerId.toString()))
                .andExpect(jsonPath("$.content[0].balance").value(100.0))
                .andExpect(jsonPath("$.totalElements").value(1));

        then(walletService).should().getAllWallets(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/admin/wallets should return 403 Forbidden for ROLE_USER (BFLA mitigation)")
    void shouldRejectGetAllWalletsForStandardUser() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when / then
        mockMvc.perform(get("/api/v1/admin/wallets")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/access-denied"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));

        then(walletService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/wallets/{id}/status should return 200 OK when ROLE_ADMIN locks wallet")
    void shouldUpdateWalletStatusForAdmin() throws Exception {
        // given
        UUID adminId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        WalletStatusUpdateRequest request = new WalletStatusUpdateRequest(WalletStatus.LOCKED);

        WalletResponse updatedResponse = new WalletResponse(
                walletId,
                ownerId,
                "Default Wallet",
                new BigDecimal("50.0000"),
                "PLN",
                WalletStatus.LOCKED,
                FIXED_INSTANT,
                FIXED_INSTANT
        );

        given(walletService.updateWalletStatus(walletId, request)).willReturn(updatedResponse);

        String payload = """
                {
                    "status": "LOCKED"
                }
                """;

        // when / then
        mockMvc.perform(patch("/api/v1/admin/wallets/{id}/status", walletId)
                        .with(SecurityTestUtils.validJwt(adminId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(walletId.toString()))
                .andExpect(jsonPath("$.status").value("LOCKED"));

        then(walletService).should().updateWalletStatus(walletId, request);
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/wallets/{id}/status should return 403 Forbidden for ROLE_USER (BFLA mitigation)")
    void shouldRejectUpdateWalletStatusForStandardUser() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String payload = """
                {
                    "status": "LOCKED"
                }
                """;

        // when / then
        mockMvc.perform(patch("/api/v1/admin/wallets/{id}/status", walletId)
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/access-denied"));

        then(walletService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/wallets/{id}/status should return 400 Bad Request when status is null")
    void shouldReturn400BadRequestWhenStatusIsNull() throws Exception {
        // given
        UUID adminId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String invalidPayload = "{}";

        // when / then
        mockMvc.perform(patch("/api/v1/admin/wallets/{id}/status", walletId)
                        .with(SecurityTestUtils.validJwt(adminId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidFields.status").value("Target wallet status must not be null"));

        then(walletService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/wallets/{id}/status should return 409 Conflict when unlocking already active wallet")
    void shouldReturn409ConflictWhenUnlockingAlreadyActiveWallet() throws Exception {
        // given
        UUID adminId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        WalletStatusUpdateRequest request = new WalletStatusUpdateRequest(WalletStatus.ACTIVE);

        given(walletService.updateWalletStatus(walletId, request))
                .willThrow(new IllegalStateException("Wallet is already active with ID: " + walletId));

        String payload = """
                {
                    "status": "ACTIVE"
                }
                """;

        // when / then
        mockMvc.perform(patch("/api/v1/admin/wallets/{id}/status", walletId)
                        .with(SecurityTestUtils.validJwt(adminId, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Wallet State Conflict"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/wallet-state-conflict"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/wallets/{id}/audit should return 200 OK with audit history for ROLE_ADMIN")
    void shouldReturnWalletAuditHistoryForAdmin() throws Exception {
        // given
        UUID adminId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        WalletAuditResponse auditEntry = new WalletAuditResponse(
                1L,
                "ADD",
                FIXED_INSTANT,
                ownerId,
                walletId,
                ownerId,
                "Default Wallet",
                new BigDecimal("0.0000"),
                "PLN",
                WalletStatus.ACTIVE
        );

        given(walletService.getWalletAuditHistory(walletId)).willReturn(List.of(auditEntry));

        // when / then
        mockMvc.perform(get("/api/v1/admin/wallets/{id}/audit", walletId)
                        .with(SecurityTestUtils.validJwt(adminId, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revisionNumber").value(1))
                .andExpect(jsonPath("$[0].revisionType").value("ADD"))
                .andExpect(jsonPath("$[0].authorId").value(ownerId.toString()))
                .andExpect(jsonPath("$[0].walletId").value(walletId.toString()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        then(walletService).should().getWalletAuditHistory(walletId);
    }

    @Test
    @DisplayName("GET /api/v1/admin/wallets/{id}/audit should return 403 Forbidden for ROLE_USER (BFLA mitigation)")
    void shouldRejectGetWalletAuditHistoryForStandardUser() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        // when / then
        mockMvc.perform(get("/api/v1/admin/wallets/{id}/audit", walletId)
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/access-denied"));

        then(walletService).shouldHaveNoInteractions();
    }
}