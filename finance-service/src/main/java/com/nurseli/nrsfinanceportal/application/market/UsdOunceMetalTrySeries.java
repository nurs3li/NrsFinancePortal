package com.nurseli.nrsfinanceportal.application.market;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * finance-service USD/ons metal TRY serisi — kıymetli maden USD/ons geçmişini tarihsel USDTRY ile TRY kapanış serisine çevirir.
 */
public final class UsdOunceMetalTrySeries {

    private UsdOunceMetalTrySeries() {}

    /**
     * {@code isUsdPerOunceMetalSymbol} — Sembolün _USD_OZ sonekli USD/ons metal olup olmadığını kontrol eder.
     */
    public static boolean isUsdPerOunceMetalSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
    return symbol.trim().toUpperCase(Locale.ROOT).endsWith("_USD_OZ");
    }

    /**
     * {@code midClosesInTry} — Metal ve USDTRY geçmişinden günlük TRY mid kapanış BigDecimal listesi üretir.
     */
    public static List<BigDecimal> midClosesInTry(
            List<MarketPriceHistoryDto> metalHistory,
            List<MarketPriceHistoryDto> usdTryHistory,
    BigDecimal spotUsdTryFallback) {
        if (metalHistory == null || metalHistory.isEmpty()) {
            return List.of();
        }
        List<MarketPriceHistoryDto> metalSorted = metalHistory.stream()
                .filter(Objects::nonNull)
                .filter(d -> d.timestamp() != null)
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .toList();
        if (metalSorted.isEmpty()) {
            return List.of();
        }
        List<MarketPriceHistoryDto> fxSorted = (usdTryHistory == null ? List.<MarketPriceHistoryDto>of() : usdTryHistory)
                .stream()
                .filter(Objects::nonNull)
                .filter(d -> d.timestamp() != null)
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .toList();

        List<BigDecimal> out = new ArrayList<>(metalSorted.size());
        for (MarketPriceHistoryDto m : metalSorted) {
            BigDecimal metalMid = midPrice(m);
            if (metalMid == null || metalMid.signum() <= 0) {
                continue;
            }
            BigDecimal rate = resolveUsdTryAt(m.timestamp(), fxSorted, spotUsdTryFallback);
            out.add(metalMid.multiply(rate).setScale(8, RoundingMode.HALF_UP));
        }
        return out;
    }

    /**
     * {@code midClosesInTryAsDoubles} — Aynı TRY serisini double listesi olarak döner.
     */
    public static List<Double> midClosesInTryAsDoubles(
            List<MarketPriceHistoryDto> metalHistory,
            List<MarketPriceHistoryDto> usdTryHistory,
    BigDecimal spotUsdTryFallback) {
        return midClosesInTry(metalHistory, usdTryHistory, spotUsdTryFallback).stream()
                .map(BigDecimal::doubleValue)
                .toList();
    }

    private static BigDecimal resolveUsdTryAt(
            LocalDateTime assetTs,
            List<MarketPriceHistoryDto> fxSorted,
            BigDecimal spotUsdTryFallback) {
        if (assetTs == null || fxSorted.isEmpty()) {
            return positiveOrOne(spotUsdTryFallback);
        }
        int lo = 0;
        int hi = fxSorted.size() - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            LocalDateTime fts = fxSorted.get(mid).timestamp();
            if (!fts.isAfter(assetTs)) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (ans >= 0) {
            BigDecimal m = midPrice(fxSorted.get(ans));
            if (m != null && m.signum() > 0) {
                return m;
            }
        }
        BigDecimal earliest = midPrice(fxSorted.getFirst());
        if (earliest != null && earliest.signum() > 0) {
            return earliest;
        }
        return positiveOrOne(spotUsdTryFallback);
    }

    private static BigDecimal midPrice(MarketPriceHistoryDto d) {
        if (d == null) {
            return null;
        }
        if (d.buyPrice() != null && d.sellPrice() != null) {
            return d.buyPrice().add(d.sellPrice()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        }
        if (d.buyPrice() != null) {
            return d.buyPrice();
        }
        return d.sellPrice();
    }

    private static BigDecimal positiveOrOne(BigDecimal spot) {
        if (spot != null && spot.signum() > 0) {
            return spot;
        }
        return BigDecimal.ONE;
    }
}
