package com.nn.walletapp_api.wallet.controller;

import com.nn.walletapp_api.shared.api.response.PageResponse;
import com.nn.walletapp_api.wallet.api.request.WalletStatusUpdateRequest;
import com.nn.walletapp_api.wallet.api.response.WalletAuditResponse;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/wallets")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService walletService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public PageResponse<WalletResponse> getAllWallets(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return walletService.getAllWallets(pageable);
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse updateWalletStatus(@PathVariable("id") UUID id,
                                             @Valid @RequestBody WalletStatusUpdateRequest request) {
        return walletService.updateWalletStatus(id, request);
    }

    @GetMapping("/{id}/audit")
    @ResponseStatus(HttpStatus.OK)
    public List<WalletAuditResponse> getWalletAuditHistory(@PathVariable("id") UUID id) {
        return walletService.getWalletAuditHistory(id);
    }
}