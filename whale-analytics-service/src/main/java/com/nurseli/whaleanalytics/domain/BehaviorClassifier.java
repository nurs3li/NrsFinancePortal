package com.nurseli.whaleanalytics.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BehaviorClassifier {

    public BehaviorClass classify(
            WhaleMetrics metrics,
            TrendResult trend,
            PatternResult pattern
    ) {

        // Manipulative pattern her zaman override eder
        if (pattern.isManipulative()) {
            return BehaviorClass.MANIPULATIVE;
        }

        //  Strategic → accumulation / distribution
        if (pattern.dominantPattern() == PatternType.ACCUMULATION
                || pattern.dominantPattern() == PatternType.DISTRIBUTION) {
            return BehaviorClass.STRATEGIC;
        }

        //  Aggressive → yüksek hacim + güçlü trend
        if (isHighVolume(metrics)
                && (trend.direction() == TrendDirection.UPWARD
                || trend.direction() == TrendDirection.DOWNWARD)
                && trend.velocity().abs().compareTo(new BigDecimal("1.0")) > 0) {

            return BehaviorClass.AGGRESSIVE;
        }

        // Speculative → volatile + orta hacim
        if (trend.direction() == TrendDirection.VOLATILE) {
            return BehaviorClass.SPECULATIVE;
        }

        // Conservative → düşük hacim + stable
        if (!isHighVolume(metrics)
                && trend.direction() == TrendDirection.STABLE) {

            return BehaviorClass.CONSERVATIVE;
        }

        return BehaviorClass.SPECULATIVE;
    }

    private boolean isHighVolume(WhaleMetrics metrics) {
        return metrics.dailyVolume()
                .compareTo(new BigDecimal("1000000")) > 0;
    }
}
