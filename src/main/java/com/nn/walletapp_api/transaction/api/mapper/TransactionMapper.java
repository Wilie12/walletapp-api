package com.nn.walletapp_api.transaction.api.mapper;

import com.nn.walletapp_api.transaction.api.response.TransactionResponse;
import com.nn.walletapp_api.transaction.domain.Transaction;
import org.mapstruct.Mapper;

@Mapper
public interface TransactionMapper {
    TransactionResponse toResponse(Transaction transaction);
}