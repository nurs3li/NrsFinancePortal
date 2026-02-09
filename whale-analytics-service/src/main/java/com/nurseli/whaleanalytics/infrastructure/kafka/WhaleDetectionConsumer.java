package com.nurseli.whaleanalytics.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.whaleanalytics.application.WhaleAnalysisService;
import com.nurseli.whaleanalytics.domain.WhaleLevel;
import com.nurseli.whaleanalytics.event.TransactionCreatedEvent;
import com.nurseli.whaleanalytics.event.WhaleAlertDetectedEvent;
import com.nurseli.whaleanalytics.infrastructure.kafka.WhaleAlertProducer;
import com.nurseli.whaleanalytics.infrastructure.redis.RedisWhaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhaleDetectionConsumer {

    private final RedisWhaleRepository whaleRepository;
    private final WhaleAnalysisService analysisService;
    private final ObjectMapper objectMapper;
    private final WhaleAlertProducer whaleAlertProducer;

    @KafkaListener(
            topics = "finance.transaction.created",
            groupId = "whale-detector-8",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(byte[] payload) {

        try {
            TransactionCreatedEvent event =
                    objectMapper.readValue(payload, TransactionCreatedEvent.class);

            Long userId = event.userId();

            // 1️⃣ METRICS GÜNCELLEME (AYNI)
            whaleRepository.incrementHourlyCount(userId);
            whaleRepository.addDailyVolume(userId, event.amount());
            whaleRepository.updateMaxTransaction(userId, event.amount());

            // 2️⃣ ANALYSIS (AYNI)
            var result = analysisService.analyze(userId.toString());

            // 3️⃣ KANIT LOG (AYNI)
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

            // 4️⃣ EVENT FIRLAT (UYUMLU)
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

                whaleAlertProducer.publish(
                        new WhaleAlertDetectedEvent(
                                Long.valueOf(result.userId()),                 // Long
                                result.level(),                                // WhaleLevel
                                result.impactScore(),                          // ✅ int
                                result.metrics().dailyVolume(),                // BigDecimal
                                result.metrics().hourlyTransactionCount(),     // int
                                result.metrics().maxSingleTransaction(),       // BigDecimal
                                Instant.now()                                  // Instant
                        )
                );

            }

        } catch (Exception e) {
            log.error("Kafka message parse error", e);
        }
    }
}
