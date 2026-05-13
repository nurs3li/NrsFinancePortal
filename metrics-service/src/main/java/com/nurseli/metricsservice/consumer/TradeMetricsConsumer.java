package com.nurseli.metricsservice.consumer;

import com.nurseli.metricsservice.event.TradeCreatedEvent;
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
public class TradeMetricsConsumer {

    private final OpenSearchIndexerService indexer;

    @KafkaListener(
            topics = "finance.trade.created",
            groupId = "metrics-trade-consumer",
            containerFactory = "tradeKafkaListenerContainerFactory"
    )
    public void consume(TradeCreatedEvent event) {
        log.info("[METRICS][TRADE] tradeId={} userId={} symbol={} type={} quantity={} totalTry={}",
                event.tradeId(), event.userId(), event.symbol(), event.tradeType(), event.quantity(), event.totalTry());
        indexer.indexTrade(event);
    }
}