package com.nurseli.metricsservice.consumer;

import com.nurseli.metricsservice.event.InvestorBehaviorUpdatedEvent;
import com.nurseli.metricsservice.opensearch.OpenSearchIndexerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.metrics", name = "investor-behavior-indexing-enabled", havingValue = "true", matchIfMissing = true)
public class InvestorBehaviorMetricsConsumer {

    private final OpenSearchIndexerService indexer;

    @KafkaListener(
            topics = "${app.kafka.topics.investor-behavior-updated:investor.behavior.updated}",
            groupId = "metrics-investor-behavior-consumer",
            containerFactory = "investorBehaviorKafkaListenerContainerFactory"
    )
    public void consume(InvestorBehaviorUpdatedEvent event) {
        log.info("[METRICS][INVESTOR] userId={} level={} impactScore={} totalPortfolioTry={}",
                event.userId(), event.investorLevel(), event.portfolioImpactScore(), event.totalPortfolioValueTry());
        indexer.indexInvestorBehavior(event);
    }
}
