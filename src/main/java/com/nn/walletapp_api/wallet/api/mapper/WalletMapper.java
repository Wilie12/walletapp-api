package com.nn.walletapp_api.wallet.api.mapper;

import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.Wallet;
import org.mapstruct.Mapper;

@Mapper
public interface WalletMapper {
    WalletResponse toResponse(Wallet wallet);
}
