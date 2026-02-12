package com.nurseli.whaleanalytics.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Component
public class PatternDetector {

    private static final int MIN_POINTS = 6;

    public PatternResult detect(List<TransactionSnapshot> history) {

        if (history == null || history.size() < MIN_POINTS) {
            return new PatternResult(PatternType.NONE, 0);
        }

        List<TransactionSnapshot> sorted = history.stream()
                .sorted(Comparator.comparing(TransactionSnapshot::occurredAt))
                .toList();

        // 1️⃣ HIGH FREQUENCY CHECK
        PatternResult hf = detectHighFrequency(sorted);
        if (hf.dominantPattern() != PatternType.NONE) return hf;

        // 2️⃣ PUMP & DUMP CHECK
        PatternResult pump = detectPumpAndDump(sorted);
        if (pump.dominantPattern() != PatternType.NONE) return pump;

        // 3️⃣ ACCUMULATION / DISTRIBUTION
        PatternResult acc = detectAccumulationOrDistribution(sorted);
        if (acc.dominantPattern() != PatternType.NONE) return acc;

        return new PatternResult(PatternType.NONE, 0);
    }

    /* ============================= */

    private PatternResult detectHighFrequency(List<TransactionSnapshot> history) {

        long shortIntervals = 0;

        for (int i = 1; i < history.size(); i++) {
            long seconds = Duration.between(
                    history.get(i - 1).occurredAt(),
                    history.get(i).occurredAt()
            ).toSeconds();

            if (seconds < 30) shortIntervals++;
        }

        if (shortIntervals > history.size() / 2) {
            return new PatternResult(PatternType.HIGH_FREQUENCY, 80);
        }

        return new PatternResult(PatternType.NONE, 0);
    }

    /* ============================= */

    private PatternResult detectPumpAndDump(List<TransactionSnapshot> history) {

        BigDecimal max = history.stream()
                .map(TransactionSnapshot::amount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal min = history.stream()
                .map(TransactionSnapshot::amount)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        if (max.compareTo(BigDecimal.ZERO) == 0) {
            return new PatternResult(PatternType.NONE, 0);
        }

        BigDecimal ratio = max.divide(min.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP);

        if (ratio.compareTo(new BigDecimal("5")) > 0) {
            return new PatternResult(PatternType.PUMP_AND_DUMP, 90);
        }

        return new PatternResult(PatternType.NONE, 0);
    }

    /* ============================= */

    private PatternResult detectAccumulationOrDistribution(List<TransactionSnapshot> history) {

        int increasing = 0;
        int decreasing = 0;

        for (int i = 1; i < history.size(); i++) {

            BigDecimal prev = history.get(i - 1).amount();
            BigDecimal cur = history.get(i).amount();

            if (cur.compareTo(prev) > 0) increasing++;
            if (cur.compareTo(prev) < 0) decreasing++;
        }

        if (increasing > history.size() * 0.6) {
            return new PatternResult(PatternType.ACCUMULATION, 70);
        }

        if (decreasing > history.size() * 0.6) {
            return new PatternResult(PatternType.DISTRIBUTION, 70);
        }

        return new PatternResult(PatternType.NONE, 0);
    }
}
