package com.nurseli.whaleanalytics.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.whaleanalytics.application.WhaleAnalysisService;
import com.nurseli.whaleanalytics.domain.WhaleLevel;
import com.nurseli.whaleanalytics.event.TransactionCreatedEvent;
import com.nurseli.whaleanalytics.event.WhaleAlertDetectedEvent;
import com.nurseli.whaleanalytics.infrastructure.redis.RedisWhaleRepository;
import com.nurseli.whaleanalytics.infrastructure.kafka.WhaleAlertProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.nurseli.whaleanalytics.event.WhaleAlertDetectedEvent;

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
            groupId = "whale-detector-v2",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(byte[] payload) {

        try {
            TransactionCreatedEvent event =
                    objectMapper.readValue(payload, TransactionCreatedEvent.class);

            Long userId = event.userId();

            // 1️⃣ metrics
            whaleRepository.incrementHourlyCount(userId);
            whaleRepository.addDailyVolume(userId, event.amount());
            whaleRepository.updateMaxTransaction(userId, event.amount());

            // 2️⃣ analysis
            var result = analysisService.analyze(userId.toString());

            // 3️⃣ KANIT LOG
            log.warn("""
                    🐋 WHALE ANALYSIS
                    userId        : {}
                    level         : {}
                    dailyVolume   : {}
                    hourlyCount   : {}
                    maxSingleTx   : {}
                    """,
                    result.userId(),
                    result.level(),
                    result.metrics().dailyVolume(),
                    result.metrics().hourlyTransactionCount(),
                    result.metrics().maxSingleTransaction()
            );


            if (result.level() != WhaleLevel.NONE) {

                log.warn("""
                         WHALE EVENT FIRLATILIYOR
                        userId : {}
                        level  : {}
                        """,
                        result.userId(),
                        result.level()
                );

                whaleAlertProducer.publish(
                        new WhaleAlertDetectedEvent(
                                Long.valueOf(result.userId()),                    // Long
                                result.level(),                                   // WhaleLevel (name() YOK)
                                result.metrics().dailyVolume(),                   // BigDecimal
                                result.metrics().hourlyTransactionCount(),        // int
                                result.metrics().maxSingleTransaction(),          // BigDecimal
                                Instant.now()                                     // Instant
                        )
                );


            }

        } catch (Exception e) {
            log.error("Kafka message parse error", e);
        }
    }
}
