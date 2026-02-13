package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;
import static com.nurseli.nrsfinanceportal.integration.kafka.KafkaTopics.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /* ================= CREATED ================= */

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {

        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);

        var message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, TRANSACTION_CREATED)
                .setHeader(KafkaHeaders.KEY, event.transactionId().toString())

                .setHeader(CORRELATION_ID_HEADER, correlationId)
                .build();

        kafkaTemplate.send(message);

        log.info(
                "[KAFKA][CREATED] topic={} txId={} accountId={} amount={} correlationId={}",
                TRANSACTION_CREATED,
                event.transactionId(),
                event.accountId(),
                event.amount(),
                correlationId
        );
    }

    /* ================= REVERSED ================= */

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionReversed(TransactionReversedEvent event) {

        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);

        var message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, TRANSACTION_REVERSED)
                .setHeader(KafkaHeaders.KEY, event.reversalTransactionId().toString())
                .setHeader(CORRELATION_ID_HEADER, correlationId)
                .build();

        kafkaTemplate.send(message);

        log.info(
                "[KAFKA][REVERSED] topic={} reversalTxId={} originalTxId={} amount={} correlationId={}",
                TRANSACTION_REVERSED,
                event.reversalTransactionId(),
                event.originalTransactionId(),
                event.amount(),
                correlationId
        );
    }
}