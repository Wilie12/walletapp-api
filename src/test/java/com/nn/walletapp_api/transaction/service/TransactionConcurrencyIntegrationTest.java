package com.nn.walletapp_api.transaction.service;

import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.support.BaseIntegrationTest;
import com.nn.walletapp_api.transaction.api.request.TransferRequest;
import com.nn.walletapp_api.transaction.repository.TransactionRepository;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.exception.InsufficientFundsException;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("Should prevent double-spending when 10 concurrent threads attempt to spend entire balance")
    void shouldPreventDoubleSpendingUnderConcurrentLoad() throws Exception {
        // given
        Instant now = Instant.now(clock);
        UUID senderOwnerId = UUID.randomUUID();
        UUID recipientOwnerId = UUID.randomUUID();
        RequesterContext senderRequester = new RequesterContext(senderOwnerId, AccessLevel.STANDARD);

        Wallet senderWallet = Wallet.createDefault(senderOwnerId, now);
        senderWallet.credit(new BigDecimal("100.0000"));
        walletRepository.saveAndFlush(senderWallet);

        Wallet recipientWallet = Wallet.createDefault(recipientOwnerId, now);
        walletRepository.saveAndFlush(recipientWallet);

        int threadCount = 10;
        BigDecimal transferAmount = new BigDecimal("100.0000");

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientFundsCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new ArrayList<>();

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        TransferRequest request = new TransferRequest(
                                senderWallet.getId(),
                                recipientWallet.getId(),
                                transferAmount,
                                "Concurrent double-spend attempt"
                        );
                        transactionService.transfer(UUID.randomUUID(), request, senderRequester);
                        successCount.incrementAndGet();
                    } catch (InsufficientFundsException ex) {
                        insufficientFundsCount.incrementAndGet();
                    } catch (Throwable t) {
                        synchronized (unexpectedErrors) {
                            unexpectedErrors.add(t);
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        // then
        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(insufficientFundsCount.get()).isEqualTo(threadCount - 1);

        Wallet updatedSender = walletRepository.findById(senderWallet.getId()).orElseThrow();
        Wallet updatedRecipient = walletRepository.findById(recipientWallet.getId()).orElseThrow();

        assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(transactionRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should prevent deadlocks and preserve total balance during concurrent opposite P2P transfers (A -> B and B -> A)")
    void shouldPreventDeadlocksOnConcurrentOppositeTransfers() throws Exception {
        // given
        Instant now = Instant.now(clock);
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        RequesterContext requesterA = new RequesterContext(ownerA, AccessLevel.STANDARD);
        RequesterContext requesterB = new RequesterContext(ownerB, AccessLevel.STANDARD);

        Wallet walletA = Wallet.createDefault(ownerA, now);
        walletA.credit(new BigDecimal("500.0000"));
        walletRepository.saveAndFlush(walletA);

        Wallet walletB = Wallet.createDefault(ownerB, now);
        walletB.credit(new BigDecimal("500.0000"));
        walletRepository.saveAndFlush(walletB);

        int transfersPerDirection = 5;
        int totalThreads = transfersPerDirection * 2;
        BigDecimal amountAToB = new BigDecimal("20.0000");
        BigDecimal amountBToA = new BigDecimal("15.0000");

        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger completedTransfers = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new ArrayList<>();

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(totalThreads)) {
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < transfersPerDirection; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        TransferRequest requestAToB = new TransferRequest(
                                walletA.getId(),
                                walletB.getId(),
                                amountAToB,
                                "Concurrent transfer A -> B"
                        );
                        transactionService.transfer(UUID.randomUUID(), requestAToB, requesterA);
                        completedTransfers.incrementAndGet();
                    } catch (Throwable t) {
                        synchronized (unexpectedErrors) {
                            unexpectedErrors.add(t);
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                }));

                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        TransferRequest requestBToA = new TransferRequest(
                                walletB.getId(),
                                walletA.getId(),
                                amountBToA,
                                "Concurrent transfer B -> A"
                        );
                        transactionService.transfer(UUID.randomUUID(), requestBToA, requesterB);
                        completedTransfers.incrementAndGet();
                    } catch (Throwable t) {
                        synchronized (unexpectedErrors) {
                            unexpectedErrors.add(t);
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        // then
        assertThat(unexpectedErrors).isEmpty();
        assertThat(completedTransfers.get()).isEqualTo(totalThreads);

        Wallet updatedWalletA = walletRepository.findById(walletA.getId()).orElseThrow();
        Wallet updatedWalletB = walletRepository.findById(walletB.getId()).orElseThrow();

        // Wallet A: 500 - (5 * 20) + (5 * 15) = 475.0000
        // Wallet B: 500 + (5 * 20) - (5 * 15) = 525.0000
        assertThat(updatedWalletA.getBalance()).isEqualByComparingTo(new BigDecimal("475.0000"));
        assertThat(updatedWalletB.getBalance()).isEqualByComparingTo(new BigDecimal("525.0000"));
        assertThat(updatedWalletA.getBalance().add(updatedWalletB.getBalance()))
                .isEqualByComparingTo(new BigDecimal("1000.0000"));
        assertThat(transactionRepository.count()).isEqualTo(totalThreads);
    }
}