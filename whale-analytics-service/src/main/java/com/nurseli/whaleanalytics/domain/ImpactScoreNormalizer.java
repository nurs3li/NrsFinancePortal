package com.nurseli.whaleanalytics.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ImpactScoreNormalizer {

    public int normalize(
            WhaleMetrics metrics,
            WhaleLevel level,
            TrendResult trend,
            PatternResult pattern,
            BehaviorClass behavior,
            RiskLevel risk,
            int rawImpactScore
    ) {

        int score = clamp(rawImpactScore);

        //  WhaleLevel boost
        switch (level) {
            case L3_MEGA_WHALE -> score += 10;
            case L2_WHALE -> score += 5;
            default -> {}
        }

        // Trend influence
        if (trend.direction() == TrendDirection.UPWARD
                || trend.direction() == TrendDirection.DOWNWARD) {

            if (trend.velocity().abs().compareTo(new BigDecimal("1.0")) > 0) {
                score += 7;
            } else {
                score += 3;
            }
        }

        if (trend.direction() == TrendDirection.VOLATILE) {
            score += 5;
        }

        // Pattern influence
        switch (pattern.dominantPattern()) {
            case ACCUMULATION -> score += 4;
            case DISTRIBUTION -> score += 4;
            case PUMP_AND_DUMP -> score += 12;
            case HIGH_FREQUENCY -> score += 10;
            default -> {}
        }

        //  Behavior influence
        switch (behavior) {
            case AGGRESSIVE -> score += 5;
            case STRATEGIC -> score += 3;
            case MANIPULATIVE -> score += 8;
            case SPECULATIVE -> score += 2;
            default -> {}
        }

        //  Risk modifier
        switch (risk) {
            case CRITICAL -> score += 5;
            case HIGH -> score += 3;
            case LOW -> score -= 2;
            default -> {}
        }

        return clamp(score);
    }

    private int clamp(int value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }
}
