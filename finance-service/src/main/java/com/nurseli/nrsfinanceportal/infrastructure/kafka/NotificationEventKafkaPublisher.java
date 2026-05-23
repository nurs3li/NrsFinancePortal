package com.nurseli.nrsfinanceportal.infrastructure.kafka;

import com.nurseli.nrsfinanceportal.infrastructure.kafka.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Bildirim isteği event'lerini Kafka'ya yayımlar.
 */
import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * NotificationRequestedEvent'i Kafka topic'ine gönderir.
     */
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