package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * finance-service tarihsel USDTRY dönüşüm yardımcısı — USD kotasyonlu varlık geçmişini TRY'ye çevirmek için kur çözümlemesi sağlar.
 */
public final class HistoricalUsdTryConversion {

    private HistoricalUsdTryConversion() {
}

    /**
     * {@code needsHistoricalUsdTry} — Varlık türünün tarihsel USDTRY çarpanı gerektirip gerektirmediğini döner.
     */
    public static boolean needsHistoricalUsdTry(AssetType type, String symbol) {
        if (type == null) {
            return false;
        }
        if (type == AssetType.STOCK && isBistSymbol(symbol)) {
            return false;
        }
        return type == AssetType.STOCK
                || type == AssetType.CRYPTO
                || type == AssetType.FUND
                || (type == AssetType.METAL && isUsdQuotedMetalSymbol(symbol));
    }

    /**
     * {@code isBistSymbol} — Sembolün BIST hissesi olup olmadığını kontrol eder.
     */
    public static boolean isBistSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        return symbol.trim().toUpperCase().endsWith(".IS");
    }

    /**
     * {@code isUsdQuotedMetalSymbol} — Sembolün USD/ons kotasyonlu metal olup olmadığını kontrol eder.
     */
    public static boolean isUsdQuotedMetalSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        return symbol.trim().toUpperCase().endsWith("_USD_OZ");
    }

    /**
     * {@code sortFxHistory} — USDTRY geçmiş kayıtlarını zamana göre sıralar.
     */
    public static List<MarketPriceHistoryDto> sortFxHistory(List<MarketPriceHistoryDto> fx) {
        if (fx == null) {
            return List.of();
        }
        return fx.stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .toList();
    }

    /**
     * {@code spotUsdTry} — Son fiyat snapshot'ından USDTRY spot mid kurunu okur.
     */
    public static BigDecimal spotUsdTry(LatestPricingSnapshot snap) {
        if (snap == null) {
            return BigDecimal.ONE;
        }
        var usdTry = snap.fx().get("USDTRY");
        if (usdTry == null || usdTry.buyPrice() == null || usdTry.buyPrice().signum() <= 0) {
            return BigDecimal.ONE;
        }
        return usdTry.buyPrice();
    }

    /**
     * {@code mid} — MarketPriceHistoryDto satırından mid fiyat hesaplar.
     */
    public static BigDecimal mid(MarketPriceHistoryDto r) {
        if (r == null) {
            return null;
        }
        boolean b = r.buyPrice() != null && r.buyPrice().signum() > 0;
        boolean s = r.sellPrice() != null && r.sellPrice().signum() > 0;
        if (b && s) {
            return r.buyPrice().add(r.sellPrice()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        }
        if (b) {
            return r.buyPrice();
        }
        if (s) {
            return r.sellPrice();
        }
        return null;
    }

    /**
     * {@code resolveUsdTryAt} — Belirli bir zamana kadar geçerli son USDTRY mid kurunu binary search ile bulur.
     */
    public static BigDecimal resolveUsdTryAt(
            LocalDateTime assetTs,
            List<MarketPriceHistoryDto> fxSorted,
            BigDecimal spotUsdTryFallback
    ) {
        if (assetTs == null || fxSorted == null || fxSorted.isEmpty()) {
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
            BigDecimal m = mid(fxSorted.get(ans));
            if (m != null && m.signum() > 0) {
                return m;
            }
        }
        BigDecimal earliest = mid(fxSorted.get(0));
        if (earliest != null && earliest.signum() > 0) {
            return earliest;
        }
        return positiveOrOne(spotUsdTryFallback);
    }

    /**
     * {@code usdToTryAt} — USD tutarını belirtilen tarihteki kurla TRY'ye çevirir.
     */
    public static BigDecimal usdToTryAt(
            BigDecimal usdPrice,
            LocalDateTime at,
    List<MarketPriceHistoryDto> fxSorted,
            BigDecimal spotUsdTryFallback
    ) {
        if (usdPrice == null || usdPrice.signum() <= 0) {
            return null;
        }
        BigDecimal rate = resolveUsdTryAt(at, fxSorted, spotUsdTryFallback);
        return usdPrice.multiply(rate).setScale(8, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveOrOne(BigDecimal spot) {
        if (spot != null && spot.signum() > 0) {
            return spot;
        }
        return BigDecimal.ONE;
    }
}
