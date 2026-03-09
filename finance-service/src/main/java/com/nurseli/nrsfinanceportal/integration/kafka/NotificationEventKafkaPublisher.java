package com.nurseli.nrsfinanceportal.integration.kafka;

import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(NotificationRequestedEvent event) {
        if (event == null || event.targetKeycloakSub() == null || event.targetKeycloakSub().isBlank()) {
            log.warn("[KAFKA][NOTIFICATION] Skipping event with empty targetKeycloakSub");
            return;
        }
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        var message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, KafkaTopics.NOTIFICATION_EVENTS)
                .setHeader(KafkaHeaders.KEY, event.targetKeycloakSub())
                .setHeader(CORRELATION_ID_HEADER, correlationId != null ? correlationId : "")
                .build();
        kafkaTemplate.send(message);
        log.info("[KAFKA][NOTIFICATION] topic={} sub={} type={} correlationId={}",
                KafkaTopics.NOTIFICATION_EVENTS, event.targetKeycloakSub(), event.type(), correlationId);
    }
}