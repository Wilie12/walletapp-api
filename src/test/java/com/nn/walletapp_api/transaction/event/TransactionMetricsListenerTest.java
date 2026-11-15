package com.nn.walletapp_api.transaction.event;

import com.nn.walletapp_api.transaction.domain.TransactionStatus;
import com.nn.walletapp_api.transaction.domain.TransactionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionMetricsListenerTest {

    private SimpleMeterRegistry meterRegistry;
    private TransactionMetricsListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new TransactionMetricsListener(meterRegistry);
    }

    @Test
    @DisplayName("Should increment counter with low-cardinality tags for completed TOP_UP transaction")
    void shouldIncrementCounterForCompletedTopUp() {
        // given
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                UUID.randomUUID(),
                TransactionType.TOP_UP,
                TransactionStatus.COMPLETED,
                new BigDecimal("150.0000")
        );

        // when
        listener.onTransactionCompleted(event);

        // then
        Counter counter = meterRegistry.find(TransactionMetricsListener.TRANSACTIONS_TOTAL_METRIC)
                .tag(TransactionMetricsListener.TAG_TYPE, "TOP_UP")
                .tag(TransactionMetricsListener.TAG_STATUS, "COMPLETED")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(counter.getId().getTags())
                .extracting(Tag::getKey)
                .containsExactlyInAnyOrder("type", "status");
    }

    @Test
    @DisplayName("Should partition counters independently by transaction type and status")
    void shouldPartitionCountersByTypeAndStatus() {
        // given
        TransactionCompletedEvent topUpEvent = new TransactionCompletedEvent(
                UUID.randomUUID(),
                TransactionType.TOP_UP,
                TransactionStatus.COMPLETED,
                new BigDecimal("100.0000")
        );
        TransactionCompletedEvent firstTransferEvent = new TransactionCompletedEvent(
                UUID.randomUUID(),
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                new BigDecimal("40.0000")
        );
        TransactionCompletedEvent secondTransferEvent = new TransactionCompletedEvent(
                UUID.randomUUID(),
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                new BigDecimal("60.0000")
        );

        // when
        listener.onTransactionCompleted(topUpEvent);
        listener.onTransactionCompleted(firstTransferEvent);
        listener.onTransactionCompleted(secondTransferEvent);

        // then
        Counter topUpCounter = meterRegistry.find(TransactionMetricsListener.TRANSACTIONS_TOTAL_METRIC)
                .tag(TransactionMetricsListener.TAG_TYPE, "TOP_UP")
                .tag(TransactionMetricsListener.TAG_STATUS, "COMPLETED")
                .counter();

        Counter transferCounter = meterRegistry.find(TransactionMetricsListener.TRANSACTIONS_TOTAL_METRIC)
                .tag(TransactionMetricsListener.TAG_TYPE, "TRANSFER")
                .tag(TransactionMetricsListener.TAG_STATUS, "COMPLETED")
                .counter();

        assertThat(topUpCounter).isNotNull();
        assertThat(topUpCounter.count()).isEqualTo(1.0);

        assertThat(transferCounter).isNotNull();
        assertThat(transferCounter.count()).isEqualTo(2.0);
    }
}