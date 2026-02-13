package com.nurseli.metricsservice.consumer;

import com.nurseli.metricsservice.event.WhaleAlertDetectedEvent;
import com.nurseli.metricsservice.opensearch.OpenSearchIndexerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhaleMetricsConsumer {

    private final OpenSearchIndexerService indexer;

    @KafkaListener(
            topics = "whale.alert.triggered",
            groupId = "metrics-whale-consumer",
            containerFactory = "whaleKafkaListenerContainerFactory"
    )
    public void consume(WhaleAlertDetectedEvent event) {
        log.info("[METRICS][WHALE] userId={} level={} impactScore={} dailyVolume={} hourlyCount={}",
                event.userId(), event.whaleLevel(), event.impactScore(), event.dailyVolume(), event.hourlyTransactionCount());
        indexer.indexWhale(event);
    }
}