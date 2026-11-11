package com.nn.walletapp_api.transaction.repository;

import com.nn.walletapp_api.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsByIdempotencyKey(UUID idempotencyKey);

    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    @Query(value = "SELECT nextval('transaction_number_seq')", nativeQuery = true)
    long getNextReferenceSequenceValue();

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.senderWalletId = :walletId
               OR t.recipientWalletId = :walletId
            """)
    Page<Transaction> findAllByWalletId(@Param("walletId") UUID walletId, Pageable pageable);
}