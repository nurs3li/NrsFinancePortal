package com.nurseli.logconsumer.consumer;

import com.nurseli.logconsumer.event.TransactionCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionCreatedConsumer {

    @KafkaListener(
            topics = "finance.transaction.created",
            groupId = "log-consumer-created-1",
            containerFactory = "createdKafkaListenerContainerFactory"
    )
    public void consume(TransactionCreatedEvent event) {

        log.info(
                "[KAFKA][CREATED] txId={} accountId={} amount={} at={}",
                event.transactionId(),
                event.accountId(),
                event.amount(),
                event.occurredAt()
        );
    }
}
