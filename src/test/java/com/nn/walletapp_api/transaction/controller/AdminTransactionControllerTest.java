package com.nn.walletapp_api.transaction.controller;

import com.nn.walletapp_api.shared.api.response.PageResponse;
import com.nn.walletapp_api.support.BaseControllerTest;
import com.nn.walletapp_api.support.SecurityTestUtils;
import com.nn.walletapp_api.transaction.api.response.TransactionResponse;
import com.nn.walletapp_api.transaction.domain.TransactionStatus;
import com.nn.walletapp_api.transaction.domain.TransactionType;
import com.nn.walletapp_api.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminTransactionController.class)
public class AdminTransactionControllerTest extends BaseControllerTest {

    @MockitoBean
    private TransactionService transactionService;

    @Test
    @DisplayName("GET /api/v1/admin/transactions should return 200 OK with global transaction ledger for ROLE_ADMIN")
    void shouldReturnGlobalTransactionLedgerForAdmin() throws Exception {
        // given
        UUID adminId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID senderWalletId = UUID.randomUUID();
        UUID recipientWalletId = UUID.randomUUID();

        TransactionResponse txResponse = new TransactionResponse(
                transactionId,
                "TRX0000100",
                UUID.randomUUID(),
                senderWalletId,
                recipientWalletId,
                new BigDecimal("320.0000"),
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                "Global ledger entry",
                FIXED_INSTANT
        );
        PageResponse<TransactionResponse> pageResponse = new PageResponse<>(
                List.of(txResponse),
                0,
                20,
                1L,
                1,
                true,
                true
        );

        given(transactionService.getAllTransactions(any(Pageable.class))).willReturn(pageResponse);

        // when / then
        mockMvc.perform(get("/api/v1/admin/transactions")
                        .with(SecurityTestUtils.validJwt(adminId, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transactionId.toString()))
                .andExpect(jsonPath("$.content[0].referenceNumber").value("TRX0000100"))
                .andExpect(jsonPath("$.content[0].senderWalletId").value(senderWalletId.toString()))
                .andExpect(jsonPath("$.content[0].recipientWalletId").value(recipientWalletId.toString()))
                .andExpect(jsonPath("$.content[0].amount").value(320.0))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalElements").value(1));

        then(transactionService).should().getAllTransactions(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/admin/transactions should return 403 Forbidden for ROLE_USER (BFLA mitigation)")
    void shouldRejectGetAllTransactionsForStandardUser() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when / then
        mockMvc.perform(get("/api/v1/admin/transactions")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/access-denied"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));

        then(transactionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /api/v1/admin/transactions should return 401 Unauthorized when JWT is missing")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        // given / when / then
        mockMvc.perform(get("/api/v1/admin/transactions"))
                .andExpect(status().isUnauthorized());

        then(transactionService).shouldHaveNoInteractions();
    }
}