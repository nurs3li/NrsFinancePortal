package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import static com.nurseli.nrsfinanceportal.integration.kafka.KafkaTopics.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /* ================= CREATED ================= */

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {

        kafkaTemplate.send(
                TRANSACTION_CREATED,
                event.transactionId().toString(),
                event
        );

        log.info(
                "[KAFKA][CREATED] topic={} txId={} accountId={} amount={}",
                TRANSACTION_CREATED,
                event.transactionId(),
                event.accountId(),
                event.amount()
        );
    }

    /* ================= REVERSED ================= */

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionReversed(TransactionReversedEvent event) {

        kafkaTemplate.send(
                TRANSACTION_REVERSED,
                event.reversalTransactionId().toString(),
                event
        );

        log.info(
                "[KAFKA][REVERSED] topic={} reversalTxId={} originalTxId={} amount={}",
                TRANSACTION_REVERSED,
                event.reversalTransactionId(),
                event.originalTransactionId(),
                event.amount()
        );
    }
}
