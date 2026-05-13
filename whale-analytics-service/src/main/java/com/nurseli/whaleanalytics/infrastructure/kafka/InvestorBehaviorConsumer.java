package com.nurseli.whaleanalytics.infrastructure.kafka;

import com.nurseli.whaleanalytics.application.InvestorBehaviorAnalysisService;
import com.nurseli.whaleanalytics.event.investment.InvestmentPositionClosedEvent;
import com.nurseli.whaleanalytics.event.investment.InvestmentPositionCreatedEvent;
import com.nurseli.whaleanalytics.event.investment.InvestmentPositionUpdatedEvent;
import com.nurseli.whaleanalytics.infrastructure.redis.RedisInvestorPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.whale", name = "portfolio-behavior-analysis-enabled", havingValue = "true", matchIfMissing = true)
public class InvestorBehaviorConsumer {

    private final RedisInvestorPositionRepository positionRepository;
    private final InvestorBehaviorAnalysisService analysisService;
    private final InvestorBehaviorEventProducer behaviorEventProducer;

    @KafkaListener(
            topics = "${app.kafka.topics.investment-position-created}",
            groupId = "whale-investor-position-created",
            containerFactory = "investmentCreatedKafkaListenerContainerFactory"
    )
    public void onCreated(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            InvestmentPositionCreatedEvent event
    ) {
        handle(correlationId, event.userId(), () -> positionRepository.upsertFromCreated(event));
    }

    @KafkaListener(
            topics = "${app.kafka.topics.investment-position-updated}",
            groupId = "whale-investor-position-updated",
            containerFactory = "investmentUpdatedKafkaListenerContainerFactory"
    )
    public void onUpdated(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            InvestmentPositionUpdatedEvent event
    ) {
        handle(correlationId, event.userId(), () -> positionRepository.upsertFromUpdated(event));
    }

    @KafkaListener(
            topics = "${app.kafka.topics.investment-position-closed}",
            groupId = "whale-investor-position-closed",
            containerFactory = "investmentClosedKafkaListenerContainerFactory"
    )
    public void onClosed(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            InvestmentPositionClosedEvent event
    ) {
        handle(correlationId, event.userId(), () -> positionRepository.upsertFromClosed(event));
    }

    private void handle(String correlationId, Long userId, Runnable persist) {
        try {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put("correlationId", correlationId);
            }
            persist.run();
            behaviorEventProducer.publish(analysisService.analyze(userId));
        } catch (Exception e) {
            log.error("[INVESTOR_BEHAVIOR] consume failed userId={}", userId, e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
