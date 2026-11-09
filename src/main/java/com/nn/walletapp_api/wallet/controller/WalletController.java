package com.nn.walletapp_api.wallet.controller;

import com.nn.walletapp_api.shared.security.annotation.CurrentRequester;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.wallet.api.request.WalletUpdateRequest;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse getOrProvisionMyWallet(@CurrentRequester RequesterContext requester) {
        return walletService.getOrProvisionMyWallet(requester);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse getWalletById(@PathVariable("id") UUID id,
                                        @CurrentRequester RequesterContext requester) {
        return walletService.getWalletById(id, requester);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse updateWalletLabel(@PathVariable("id") UUID id,
                                            @Valid @RequestBody WalletUpdateRequest request,
                                            @CurrentRequester RequesterContext requester) {
        return walletService.updateWalletLabel(id, request, requester);
    }
}