package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.integration.kafka.event.SuspiciousActivityDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;
import static com.nurseli.nrsfinanceportal.integration.kafka.KafkaTopics.SUSPICIOUS_DETECTED;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuspiciousEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @org.springframework.context.event.EventListener
    public void handleSuspiciousActivity(SuspiciousActivityDetectedEvent event) {

        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);

        var message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, SUSPICIOUS_DETECTED)
                .setHeader(KafkaHeaders.KEY, event.userId() != null ? event.userId().toString() : "unknown")
                .setHeader(CORRELATION_ID_HEADER, correlationId != null ? correlationId : "")
                .build();

        kafkaTemplate.send(message);

        log.info(
                "[KAFKA][SUSPICIOUS] topic={} userId={} txId={} reason={} correlationId={}",
                SUSPICIOUS_DETECTED,
                event.userId(),
                event.transactionId(),
                event.reason(),
                correlationId
        );
    }
}