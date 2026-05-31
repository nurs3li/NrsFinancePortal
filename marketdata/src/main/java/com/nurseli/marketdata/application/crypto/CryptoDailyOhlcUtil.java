package com.nurseli.marketdata.application.crypto;

import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CryptoDailyOhlcUtil {

    private static final BigDecimal FLAT_EPS = new BigDecimal("0.000001");

    private CryptoDailyOhlcUtil() {
    }

    public static List<CoinGeckoClient.OhlcPoint> aggregateToDaily(List<CoinGeckoClient.OhlcPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, List<CoinGeckoClient.OhlcPoint>> byDay = new LinkedHashMap<>();
        for (CoinGeckoClient.OhlcPoint point : points) {
            if (point == null || point.day() == null) {
                continue;
            }
            byDay.computeIfAbsent(point.day(), ignored -> new ArrayList<>()).add(point);
        }
        List<CoinGeckoClient.OhlcPoint> out = new ArrayList<>(byDay.size());
        for (Map.Entry<LocalDate, List<CoinGeckoClient.OhlcPoint>> entry : byDay.entrySet()) {
            List<CoinGeckoClient.OhlcPoint> dayPoints = entry.getValue();
            if (dayPoints.isEmpty()) {
                continue;
            }
            CoinGeckoClient.OhlcPoint first = dayPoints.get(0);
            CoinGeckoClient.OhlcPoint last = dayPoints.get(dayPoints.size() - 1);
            BigDecimal high = dayPoints.stream()
                    .map(CoinGeckoClient.OhlcPoint::high)
                    .max(Comparator.naturalOrder())
                    .orElse(first.high());
            BigDecimal low = dayPoints.stream()
                    .map(CoinGeckoClient.OhlcPoint::low)
                    .min(Comparator.naturalOrder())
                    .orElse(first.low());
            BigDecimal volume = dayPoints.stream()
                    .map(CoinGeckoClient.OhlcPoint::volume)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new CoinGeckoClient.OhlcPoint(
                    entry.getKey(),
                    first.open(),
                    high,
                    low,
                    last.close(),
                    volume.signum() > 0 ? volume : null
            ));
        }
        return out;
    }

    public static boolean isFlatPrices(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        if (open == null || high == null || low == null || close == null) {
            return true;
        }
        return open.subtract(high).abs().compareTo(FLAT_EPS) <= 0
                && high.subtract(low).abs().compareTo(FLAT_EPS) <= 0
                && low.subtract(close).abs().compareTo(FLAT_EPS) <= 0;
    }

    public static boolean isFlat(CryptoDailyCandle candle) {
        if (candle == null) {
            return true;
        }
        return isFlatPrices(
                candle.getOpenPrice(),
                candle.getHighPrice(),
                candle.getLowPrice(),
                candle.getClosePrice()
        );
    }

    public static boolean isFlat(CoinGeckoClient.OhlcPoint point) {
        if (point == null) {
            return true;
        }
        return isFlatPrices(point.open(), point.high(), point.low(), point.close());
    }
}
