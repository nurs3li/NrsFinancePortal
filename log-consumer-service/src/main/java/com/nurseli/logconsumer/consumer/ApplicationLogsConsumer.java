package com.nurseli.logconsumer.consumer;

import com.nurseli.logconsumer.opensearch.ApplicationLogIndexerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLogsConsumer {

    private final ApplicationLogIndexerService indexer;

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