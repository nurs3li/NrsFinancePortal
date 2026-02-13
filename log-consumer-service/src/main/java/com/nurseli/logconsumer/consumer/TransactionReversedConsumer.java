package com.nurseli.logconsumer.consumer;

import com.nurseli.logconsumer.event.TransactionReversedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionReversedConsumer {

    @KafkaListener(
            topics = "finance.transaction.reversed",
            groupId = "log-consumer-reversed-1",
            containerFactory = "reversedKafkaListenerContainerFactory"
    )
    public void consume(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            TransactionReversedEvent event
    ) {

        try {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put("correlationId", correlationId);
            }

            log.info(
                    "[KAFKA][REVERSED] correlationId={} key={} reversalTxId={} originalTxId={} accountId={} adminUserId={} amount={} at={}",
                    correlationId,
                    event.reversalTransactionId(),
                    event.originalTransactionId(),
                    event.accountId(),
                    event.adminUserId(),
                    event.amount(),
                    event.occurredAt()
            );
        } finally {
            MDC.remove("correlationId");
        }
    }
}