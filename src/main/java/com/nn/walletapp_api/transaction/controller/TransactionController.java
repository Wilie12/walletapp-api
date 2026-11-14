package com.nn.walletapp_api.transaction.controller;

import com.nn.walletapp_api.shared.security.annotation.CurrentRequester;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.transaction.api.request.TopUpRequest;
import com.nn.walletapp_api.transaction.api.request.TransferRequest;
import com.nn.walletapp_api.transaction.api.response.TransactionResponse;
import com.nn.walletapp_api.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class TransactionController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransactionService transactionService;

    @PostMapping("/top-up")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse topUp(@RequestHeader(IDEMPOTENCY_KEY_HEADER) UUID idempotencyKey,
                                     @Valid @RequestBody TopUpRequest request,
                                     @CurrentRequester RequesterContext requester) {
        return transactionService.topUp(idempotencyKey, request, requester);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@RequestHeader(IDEMPOTENCY_KEY_HEADER) UUID idempotencyKey,
                                        @Valid @RequestBody TransferRequest request,
                                        @CurrentRequester RequesterContext requester) {
        return transactionService.transfer(idempotencyKey, request, requester);
    }
}