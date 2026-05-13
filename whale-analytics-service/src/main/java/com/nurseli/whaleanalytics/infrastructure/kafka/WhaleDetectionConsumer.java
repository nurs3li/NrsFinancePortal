package com.nurseli.whaleanalytics.infrastructure.kafka;

import com.nurseli.whaleanalytics.application.WhaleAnalysisService;
import com.nurseli.whaleanalytics.domain.WhaleLevel;
import com.nurseli.whaleanalytics.event.TransactionCreatedEvent;
import com.nurseli.whaleanalytics.event.WhaleAlertDetectedEvent;
import com.nurseli.whaleanalytics.infrastructure.redis.RedisWhaleRepository;
import com.nurseli.whaleanalytics.config.LegacyWhaleTransactionIngestionCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Conditional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(LegacyWhaleTransactionIngestionCondition.class)
public class WhaleDetectionConsumer {

    private final RedisWhaleRepository whaleRepository;
    private final WhaleAnalysisService analysisService;
    private final WhaleAlertProducer whaleAlertProducer;

    @KafkaListener(
            topics = "finance.transaction.created",
            groupId = "whale-detector-13",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            TransactionCreatedEvent event
    ) {

        try {
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put("correlationId", correlationId);
            }

            Long userId = event.userId();

            //  METRICS GÜNCELLEME
            whaleRepository.incrementHourlyCount(userId);
            whaleRepository.addDailyVolume(userId, event.amount());
            whaleRepository.updateMaxTransaction(userId, event.amount());
            whaleRepository.addTransactionSnapshot(userId, event.amount(), event.occurredAt());

            //  ANALYSIS
            var result = analysisService.analyze(userId.toString());

            //  Özet log
            log.warn("""
                    🐋 WHALE ANALYSIS
                    userId        : {}
                    level         : {}
                    dailyVolume   : {}
                    hourlyCount   : {}
                    maxSingleTx   : {}
                    impactScore   : {}
                    """,
                    result.userId(),
                    result.level(),
                    result.metrics().dailyVolume(),
                    result.metrics().hourlyTransactionCount(),
                    result.metrics().maxSingleTransaction(),
                    result.impactScore()
            );

            // EVENT PUBLISH (tek nokta)
            if (result.level() != WhaleLevel.NONE) {

                log.warn("""
                         🚨 WHALE EVENT FIRLATILIYOR
                         userId : {}
                         level  : {}
                         score  : {}
                        """,
                        result.userId(),
                        result.level(),
                        result.impactScore()
                );

                var decision = analysisService.analyze(userId.toString());

                whaleAlertProducer.publish(
                        new WhaleAlertDetectedEvent(
                                userId,
                                decision.level(),
                                decision.impactScore(),
                                decision.metrics().dailyVolume(),
                                decision.metrics().hourlyTransactionCount(),
                                decision.metrics().maxSingleTransaction(),
                                decision.trend().direction().name(),
                                decision.trend().velocity(),
                                decision.trend().volatility(),
                                decision.pattern().dominantPattern().name(),
                                decision.behavior().name(),
                                decision.risk().name(),
                                decision.evaluatedAt()
                        )
                );
            }

        } catch (Exception e) {
            log.error("Kafka message parse error", e);
        } finally {
            MDC.remove("correlationId");
        }
    }}