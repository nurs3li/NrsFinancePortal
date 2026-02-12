package com.nurseli.logconsumer.consumer;

import com.nurseli.logconsumer.event.SuspiciousActivityDetectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SuspiciousActivityConsumer {

    @KafkaListener(
            topics = "finance.suspicious.detected",
            groupId = "log-consumer-suspicious-1",
            containerFactory = "suspiciousKafkaListenerContainerFactory"
    )
    public void consume(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            SuspiciousActivityDetectedEvent event
    ) {
        try {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put("correlationId", correlationId);
            }

            log.warn(
                    "[KAFKA][SUSPICIOUS] correlationId={} userId={} txId={} reason={} amount={} countInWindow={} thresholdAmount={} thresholdCount={} occurredAt={}",
                    correlationId,
                    event.userId(),
                    event.transactionId(),
                    event.reason(),
                    event.amount(),
                    event.countInWindow(),
                    event.thresholdAmount(),
                    event.thresholdCount(),
                    event.occurredAt()
            );
        } finally {
            MDC.remove("correlationId");
        }
    }
}