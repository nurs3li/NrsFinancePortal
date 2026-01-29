package com.nurseli.whaleanalytics.application;

import com.nurseli.whaleanalytics.domain.WhaleDecisionEngine;
import com.nurseli.whaleanalytics.domain.WhaleResult;
import com.nurseli.whaleanalytics.event.WhaleAlertDetectedEvent;
import com.nurseli.whaleanalytics.infrastructure.kafka.WhaleAlertProducer;
import com.nurseli.whaleanalytics.infrastructure.redis.RedisWhaleReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WhaleAnalysisService {

    private final RedisWhaleReadRepository readRepository;
    private final WhaleDecisionEngine decisionEngine;
    private final WhaleAlertProducer alertProducer;

    public WhaleResult analyze(String userId) {

        var metrics = readRepository.getMetrics(userId);
        var level = decisionEngine.evaluate(metrics);

        // 🚨 SADECE ALERT LEVEL (L2 / L3)
        if (level.isAlertLevel()) {

            alertProducer.publish(
                    new WhaleAlertDetectedEvent(
                            Long.valueOf(userId),                 // ✅ TEK DÖNÜŞÜM
                            level,                                // ✅ ENUM
                            metrics.dailyVolume(),                // ✅ BigDecimal
                            metrics.hourlyTransactionCount(),     // ✅ int
                            metrics.maxSingleTransaction(),        // ✅ BigDecimal
                            Instant.now()
                    )
            );
        }

        return new WhaleResult(
                userId,
                level,
                metrics,
                Instant.now()
        );
    }
}
