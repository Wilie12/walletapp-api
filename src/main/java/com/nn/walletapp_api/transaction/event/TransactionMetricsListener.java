package com.nn.walletapp_api.transaction.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionMetricsListener {

    static final String TRANSACTIONS_TOTAL_METRIC = "walletapp.transactions.total";
    static final String TAG_TYPE = "type";
    static final String TAG_STATUS = "status";

    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        Counter.builder(TRANSACTIONS_TOTAL_METRIC)
                .description("Total number of committed financial transactions partitioned by type and status")
                .tag(TAG_TYPE, event.type().name())
                .tag(TAG_STATUS, event.status().name())
                .register(meterRegistry)
                .increment();

        log.debug("Recorded AFTER_COMMIT metric for transaction {} [type={}, status={}]",
                event.transactionId(), event.type(), event.status());
    }
}