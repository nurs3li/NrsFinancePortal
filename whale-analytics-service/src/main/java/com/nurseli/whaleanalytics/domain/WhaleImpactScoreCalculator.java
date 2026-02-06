package com.nurseli.whaleanalytics.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class WhaleImpactScoreCalculator {

    public int calculate(WhaleMetrics m, WhaleLevel level) {

        int score = 0;

        // 🔹 Günlük hacim etkisi (max 40)
        score += m.dailyVolume()
                .divide(BigDecimal.valueOf(1_000_000), 2, BigDecimal.ROUND_HALF_UP)
                .min(BigDecimal.valueOf(40))
                .intValue();

        // 🔹 Saatlik yoğunluk (max 30)
        score += Math.min(m.hourlyTransactionCount() * 5, 30);

        // 🔹 Tek işlem şoku (max 20)
        score += m.maxSingleTransaction()
                .divide(BigDecimal.valueOf(500_000), 2, BigDecimal.ROUND_HALF_UP)
                .min(BigDecimal.valueOf(20))
                .intValue();

        // 🔥 WhaleLevel bonusu
        score = switch (level) {
            case L3_MEGA_WHALE -> score + 20;
            case L2_WHALE -> score + 10;
            case L1_LARGE_TRADER -> score;
            default -> 0;
        };

        return Math.min(score, 100);
    }
}
