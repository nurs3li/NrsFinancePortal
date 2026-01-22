package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventKafkaProducer {

    private static final String TOPIC = "transaction-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @EventListener
    public void handleCreated(TransactionCreatedEvent event) {
        kafkaTemplate.send(
                TOPIC,
                event.transactionId().toString(),
                event
        );

        log.info("[KAFKA] TransactionCreatedEvent sent → {}", event.transactionId());
    }

    @EventListener
    public void handleReversed(TransactionReversedEvent event) {
        kafkaTemplate.send(
                TOPIC,
                event.reversalTransactionId().toString(),
                event
        );

        log.info("[KAFKA] TransactionReversedEvent sent → {}", event.reversalTransactionId());
    }
}
