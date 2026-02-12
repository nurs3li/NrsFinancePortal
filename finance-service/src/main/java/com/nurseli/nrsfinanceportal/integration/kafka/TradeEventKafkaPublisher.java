package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.integration.kafka.event.TradeCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import static com.nurseli.nrsfinanceportal.integration.kafka.KafkaTopics.TRADE_CREATED;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTradeCreated(TradeCreatedEvent event) {

        kafkaTemplate.send(
                TRADE_CREATED,
                event.tradeId().toString(),
                event
        );

        log.info(
                "[KAFKA][TRADE_CREATED] topic={} tradeId={} userId={} symbol={} qty={}",
                TRADE_CREATED,
                event.tradeId(),
                event.userId(),
                event.symbol(),
                event.quantity()
        );
    }
}