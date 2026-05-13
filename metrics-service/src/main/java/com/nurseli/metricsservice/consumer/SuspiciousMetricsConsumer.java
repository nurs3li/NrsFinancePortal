package com.nurseli.metricsservice.consumer;

import com.nurseli.metricsservice.event.SuspiciousActivityDetectedEvent;
import com.nurseli.metricsservice.opensearch.OpenSearchIndexerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "legacy-finance-ingestion-enabled", havingValue = "true")
public class SuspiciousMetricsConsumer {

    private final OpenSearchIndexerService indexer;

    @KafkaListener(
            topics = "finance.suspicious.detected",
            groupId = "metrics-suspicious-consumer",
            containerFactory = "suspiciousKafkaListenerContainerFactory"
    )
    public void consume(SuspiciousActivityDetectedEvent event) {
        log.info("[METRICS][SUSPICIOUS] userId={} txId={} reason={} amount={}",
                event.userId(), event.transactionId(), event.reason(), event.amount());
        indexer.indexSuspicious(event);
    }
}