package com.nurseli.logconsumer.consumer;

import com.nurseli.logconsumer.event.TransactionReversedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionReversedConsumer {

    @KafkaListener(
            topics = "finance.transaction.reversed",
            groupId = "log-consumer-reversed-1",
            containerFactory = "reversedKafkaListenerContainerFactory"
    )
    public void consume(TransactionReversedEvent event) {

        log.info(
                "[KAFKA][REVERSED] originalTxId={} reversalTxId={} at={}",
                event.originalTransactionId(),
                event.reversalTransactionId(),
                event.occurredAt()
        );
    }
}
