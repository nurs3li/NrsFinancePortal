package com.nurseli.logconsumer.infrastructure.messaging;

import com.nurseli.logconsumer.application.event.TransactionCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * {@code finance.transaction.created} Kafka topic'inden gelen işlem oluşturma event'lerini dinler
 * ve yapılandırılmış audit log satırı yazar.
 */
@Slf4j
@Component
public class TransactionCreatedConsumer {

    /**
     * {@code consume} — Event alanlarını loglar; {@code X-Correlation-Id} header'ını MDC'ye taşır.
     */
    @KafkaListener(
            topics = "finance.transaction.created",
            groupId = "log-consumer-created-1",
            containerFactory = "createdKafkaListenerContainerFactory"
    )
    public void consume(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            TransactionCreatedEvent event
    ) {

        try {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put("correlationId", correlationId);
            }

            log.info(
                    "[KAFKA][CREATED] correlationId={} txId={} accountId={} userId={} type={} amount={} balanceAfter={} occurredAt={}",
                    correlationId,
                    event.transactionId(),
                    event.accountId(),
                    event.userId(),
                    event.type(),
                    event.amount(),
                    event.balanceAfter(),
                    event.occurredAt()
            );
        } finally {
            MDC.remove("correlationId");
        }
    }
}
