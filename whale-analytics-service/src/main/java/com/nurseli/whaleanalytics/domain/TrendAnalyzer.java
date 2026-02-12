package com.nurseli.whaleanalytics.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Component
public class TrendAnalyzer {

    // Çok küçük veride yanlış karar vermesin diye
    private static final int MIN_POINTS = 4;

    // Slope karar eşikleri (amount/minute) — gerekirse config’e taşırız
    private static final BigDecimal UP_SLOPE_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal DOWN_SLOPE_THRESHOLD = new BigDecimal("-0.50");

    // Volatile sayma eşiği (0..1 arası) — gerekirse config’e taşırız
    private static final BigDecimal VOLATILITY_THRESHOLD = new BigDecimal("0.35");

    public TrendResult analyze(List<TransactionSnapshot> history) {

        if (history == null || history.size() < MIN_POINTS) {
            return new TrendResult(
                    TrendDirection.STABLE,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        // 1) zamana göre sırala (eskiden yeniye)
        List<TransactionSnapshot> sorted = history.stream()
                .sorted(Comparator.comparing(TransactionSnapshot::occurredAt))
                .toList();

        // 2) rate series üret (amount per minute)
        List<BigDecimal> rates = buildRates(sorted);

        if (rates.size() < 2) {
            return new TrendResult(TrendDirection.STABLE, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // 3) velocity = son rate - ilk rate / (n-1)
        BigDecimal first = rates.get(0);
        BigDecimal last = rates.get(rates.size() - 1);
        BigDecimal velocity = last.subtract(first)
                .divide(new BigDecimal(rates.size() - 1), 8, RoundingMode.HALF_UP);

        // 4) volatility (0..1 normalize)
        BigDecimal volatility = computeVolatility(rates);

        // 5) direction kararı
        TrendDirection direction;

        if (volatility.compareTo(VOLATILITY_THRESHOLD) >= 0) {
            direction = TrendDirection.VOLATILE;
        } else if (velocity.compareTo(UP_SLOPE_THRESHOLD) > 0) {
            direction = TrendDirection.UPWARD;
        } else if (velocity.compareTo(DOWN_SLOPE_THRESHOLD) < 0) {
            direction = TrendDirection.DOWNWARD;
        } else {
            direction = TrendDirection.STABLE;
        }

        return new TrendResult(direction, velocity, volatility);
    }

    private List<BigDecimal> buildRates(List<TransactionSnapshot> sorted) {

        // rate[i] = amount[i] / minutesDiff(i-1,i)
        // ilk eleman için 0 koyuyoruz (slope hesabında sorun olmaz)
        new BigDecimal("0");

        return java.util.stream.IntStream.range(0, sorted.size())
                .mapToObj(i -> {
                    if (i == 0) return BigDecimal.ZERO;

                    TransactionSnapshot prev = sorted.get(i - 1);
                    TransactionSnapshot cur = sorted.get(i);

                    long minutes = Math.max(1, Duration.between(prev.occurredAt(), cur.occurredAt()).toMinutes());

                    // amount/minute
                    return cur.amount()
                            .divide(new BigDecimal(minutes), 8, RoundingMode.HALF_UP);
                })
                .toList();
    }

    private BigDecimal computeVolatility(List<BigDecimal> rates) {

        BigDecimal avg = rates.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(rates.size()), 8, RoundingMode.HALF_UP);

        // avg 0 ise volatility 0 kabul edelim
        if (avg.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // mean absolute deviation / avg  (yaklaşık normalize)
        BigDecimal mad = rates.stream()
                .map(r -> r.subtract(avg).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(rates.size()), 8, RoundingMode.HALF_UP);

        BigDecimal v = mad.divide(avg.abs(), 8, RoundingMode.HALF_UP);

        // 1.0 üstünü 1.0’a clamp (çok zıplamasın)
        return v.min(BigDecimal.ONE);
    }
}
