package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventKafkaProducer {

    private static final String TOPIC = "transaction-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ✅ DB commit OLDUKTAN SONRA çalışır
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreated(TransactionCreatedEvent event) {

        kafkaTemplate.send(
                TOPIC,
                event.transactionId().toString(),
                event
        );

        log.info("[KAFKA] TransactionCreatedEvent sent → {}", event.transactionId());
    }

    // ✅ DB commit OLDUKTAN SONRA çalışır
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReversed(TransactionReversedEvent event) {

        kafkaTemplate.send(
                TOPIC,
                event.reversalTransactionId().toString(),
                event
        );

        log.info("[KAFKA] TransactionReversedEvent sent → {}", event.reversalTransactionId());
    }
}

