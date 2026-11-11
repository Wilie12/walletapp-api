package com.nn.walletapp_api.transaction.controller;

import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.support.BaseControllerTest;
import com.nn.walletapp_api.support.SecurityTestUtils;
import com.nn.walletapp_api.transaction.api.request.TopUpRequest;
import com.nn.walletapp_api.transaction.api.response.TransactionResponse;
import com.nn.walletapp_api.transaction.domain.TransactionStatus;
import com.nn.walletapp_api.transaction.domain.TransactionType;
import com.nn.walletapp_api.transaction.exception.DuplicateTransactionException;
import com.nn.walletapp_api.transaction.service.TransactionService;
import com.nn.walletapp_api.wallet.exception.WalletLockedException;
import com.nn.walletapp_api.wallet.exception.WalletOwnershipException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransactionController.class)
public class TransactionControllerTest extends BaseControllerTest {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @MockitoBean
    private TransactionService transactionService;

    @Test
    @DisplayName("POST /api/v1/transactions/top-up should return 201 Created with completed transaction")
    void shouldReturn201CreatedOnValidTopUp() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        RequesterContext expectedContext = new RequesterContext(userId, AccessLevel.STANDARD);

        TopUpRequest expectedRequest = new TopUpRequest(
                walletId,
                new BigDecimal("150.2500"),
                "Account funding"
        );

        TransactionResponse response = new TransactionResponse(
                transactionId,
                "TRX0000001",
                idempotencyKey,
                null,
                walletId,
                new BigDecimal("150.2500"),
                TransactionType.TOP_UP,
                TransactionStatus.COMPLETED,
                "Account funding",
                FIXED_INSTANT
        );

        given(transactionService.topUp(idempotencyKey, expectedRequest, expectedContext))
                .willReturn(response);

        String payload = """
                {
                    "walletId": "%s",
                    "amount": 150.2500,
                    "description": "Account funding"
                }
                """.formatted(walletId);

        // when / then
        mockMvc.perform(post("/api/v1/transactions/top-up")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.referenceNumber").value("TRX0000001"))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey.toString()))
                .andExpect(jsonPath("$.senderWalletId").isEmpty())
                .andExpect(jsonPath("$.recipientWalletId").value(walletId.toString()))
                .andExpect(jsonPath("$.amount").value(150.25))
                .andExpect(jsonPath("$.type").value("TOP_UP"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.description").value("Account funding"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-31T12:00:00Z"));

        then(transactionService).should().topUp(idempotencyKey, expectedRequest, expectedContext);
    }

    @Test
    @DisplayName("POST /api/v1/transactions/top-up should return 400 Bad Request when Idempotency-Key header is missing")
    void shouldReturn400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String payload = """
                {
                    "walletId": "%s",
                    "amount": 100.0000,
                    "description": "Missing header test"
                }
                """.formatted(walletId);

        // when / then
        mockMvc.perform(post("/api/v1/transactions/top-up")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Missing Header"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/missing-header"))
                .andExpect(jsonPath("$.detail").value("Required request header 'Idempotency-Key' is not present"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));

        then(transactionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/transactions/top-up should return 400 Bad Request when amount is zero or negative")
    void shouldReturn400WhenAmountIsNotPositive() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        String invalidPayload = """
                {
                    "walletId": "%s",
                    "amount": 0.0000,
                    "description": "Zero amount"
                }
                """.formatted(walletId);

        // when / then
        mockMvc.perform(post("/api/v1/transactions/top-up")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/validation-failed"))
                .andExpect(jsonPath("$.invalidFields.amount").value("Top-up amount must be strictly positive"));

        then(transactionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/transactions/top-up should return 409 Conflict when idempotency key is duplicated")
    void shouldReturn409ConflictOnDuplicateIdempotencyKey() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        RequesterContext expectedContext = new RequesterContext(userId, AccessLevel.STANDARD);
        TopUpRequest request = new TopUpRequest(walletId, new BigDecimal("100.0000"), "Duplicate top-up");

        given(transactionService.topUp(idempotencyKey, request, expectedContext))
                .willThrow(new DuplicateTransactionException(idempotencyKey));

        String payload = """
                {
                    "walletId": "%s",
                    "amount": 100.0000,
                    "description": "Duplicate top-up"
                }
                """.formatted(walletId);

        // when / then
        mockMvc.perform(post("/api/v1/transactions/top-up")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Duplicate Transaction"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/duplicate-transaction"))
                .andExpect(jsonPath("$.instance").value("/api/v1/transactions/top-up"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/top-up should return 409 Conflict when target wallet is locked")
    void shouldReturn409ConflictWhenWalletIsLocked() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        RequesterContext expectedContext = new RequesterContext(userId, AccessLevel.STANDARD);
        TopUpRequest request = new TopUpRequest(walletId, new BigDecimal("100.0000"), "Locked wallet top-up");

        given(transactionService.topUp(idempotencyKey, request, expectedContext))
                .willThrow(new WalletLockedException("Cannot execute credit: wallet " + walletId + " is in LOCKED state"));

        String payload = """
                {
                    "walletId": "%s",
                    "amount": 100.0000,
                    "description": "Locked wallet top-up"
                }
                """.formatted(walletId);

        // when / then
        mockMvc.perform(post("/api/v1/transactions/top-up")
                        .with(SecurityTestUtils.validJwt(userId, "ROLE_USER"))
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Wallet Locked"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/wallet-locked"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/top-up should return 403 Forbidden on IDOR attempt")
    void shouldReturn403ForbiddenOnIdorAttempt() throws Exception {
        // given
        UUID attackerId = UUID.randomUUID();
        UUID victimWalletId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        RequesterContext attackerContext = new RequesterContext(attackerId, AccessLevel.STANDARD);
        TopUpRequest request = new TopUpRequest(victimWalletId, new BigDecimal("50.0000"), "IDOR top-up");

        given(transactionService.topUp(idempotencyKey, request, attackerContext))
                .willThrow(new WalletOwnershipException(
                        "Security violation: requester " + attackerId + " is not the owner of wallet " + victimWalletId
                ));

        String payload = """
                {
                    "walletId": "%s",
                    "amount": 50.0000,
                    "description": "IDOR top-up"
                }
                """.formatted(victimWalletId);

        // when / then
        mockMvc.perform(post("/api/v1/transactions/top-up")
                        .with(SecurityTestUtils.validJwt(attackerId, "ROLE_USER"))
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Wallet Ownership Violation"))
                .andExpect(jsonPath("$.type").value("https://api.walletapp.com/errors/wallet-ownership-violation"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-31T12:00:00Z"));
    }
}