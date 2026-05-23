package com.nurseli.logconsumer.infrastructure.messaging;

import com.nurseli.logconsumer.application.ApplicationLogIndexerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * {@code application-logs} Kafka topic'ini dinler; gelen JSON log payload'larını
 * {@link ApplicationLogIndexerService} ile OpenSearch'e indexler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLogsConsumer {

    private final ApplicationLogIndexerService indexer;

    /**
     * {@code consume} — Ham JSON log mesajını alır; boş değilse indexleme servisine iletir.
     */
    @KafkaListener(
            topics = "application-logs",
            groupId = "log-consumer-application-logs",
            containerFactory = "applicationLogsKafkaListenerContainerFactory"
    )
    public void consume(String jsonLogPayload) {
        log.debug("[KAFKA][APPLICATION-LOG] received payload length={}", jsonLogPayload != null ? jsonLogPayload.length() : 0);
        if (jsonLogPayload != null && !jsonLogPayload.isBlank()) {
            indexer.indexLog(jsonLogPayload);
        }
    }
}
