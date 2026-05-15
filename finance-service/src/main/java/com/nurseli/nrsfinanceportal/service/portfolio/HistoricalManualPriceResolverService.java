package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.ManualPriceResolveDto;
import com.nurseli.nrsfinanceportal.config.ManualPortfolioPriceResolveProperties;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class HistoricalManualPriceResolverService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final MarketDataClient marketDataClient;
    private final ManualPortfolioPriceResolveProperties resolveProperties;

    public ManualPriceResolveDto resolve(AssetType type, String symbol, LocalDate requestedDate) {
        if (type == null || symbol == null || symbol.isBlank() || requestedDate == null) {
            return ManualPriceResolveDto.notFound(type, symbol, requestedDate,
                    "Geçersiz parametre.");
        }
        LocalDate today = LocalDate.now(TZ);
        if (requestedDate.isAfter(today)) {
            return ManualPriceResolveDto.notFound(type, symbol, requestedDate,
                    "Gelecek tarih için fiyat çözülemez.");
        }
        LatestPricingSnapshot snap = marketDataClient.loadLatestPricing();
        int maxLb = Math.max(1, resolveProperties.getMaxLookbackDays());
        long spanDays = ChronoUnit.DAYS.between(requestedDate, today) + maxLb;
        int allowed = AllowedHistoryDays.smallestCovering(spanDays);
        if (allowed < 0) {
            return ManualPriceResolveDto.notFound(type, symbol, requestedDate,
                    "Seçilen tarih için fiyat bulunamadı. Manuel fiyat girin.");
        }

        List<MarketPriceHistoryDto> raw = loadHistory(type, symbol, requestedDate, today, maxLb, allowed);
        NavigableMap<LocalDate, BigDecimal> byDay = aggregateLastPricePerDay(type, symbol, raw, snap);
        if (byDay.isEmpty()) {
            return ManualPriceResolveDto.notFound(type, symbol, requestedDate,
                    "Seçilen tarih için fiyat bulunamadı. Manuel fiyat girin.");
        }

        BigDecimal exactPx = byDay.get(requestedDate);
        if (exactPx != null) {
            return ManualPriceResolveDto.ok(type, symbol, requestedDate, requestedDate, exactPx,
                    ManualPriceSource.MARKET_HISTORY_EXACT, "TRY", null);
        }

        LocalDate lower = requestedDate.minusDays(maxLb);
        BigDecimal bestPrice = null;
        LocalDate bestDate = null;
        for (Map.Entry<LocalDate, BigDecimal> e : byDay.entrySet()) {
            LocalDate d = e.getKey();
            if (d.isBefore(lower)) {
                continue;
            }
            if (!d.isBefore(requestedDate)) {
                continue;
            }
            if (e.getValue() != null && e.getValue().signum() > 0) {
                if (bestDate == null || d.isAfter(bestDate)) {
                    bestPrice = e.getValue();
                    bestDate = d;
                }
            }
        }
        if (bestPrice != null && bestDate != null) {
            return ManualPriceResolveDto.ok(type, symbol, requestedDate, bestDate, bestPrice,
                    ManualPriceSource.MARKET_HISTORY_PREVIOUS_CLOSE, "TRY",
                    "Seçilen tarihte fiyat bulunamadı; en yakın önceki kapanış kullanıldı.");
        }
        return ManualPriceResolveDto.notFound(type, symbol, requestedDate,
                "Seçilen tarih için fiyat bulunamadı. Manuel fiyat girin.");
    }

    /**
     * Günlük kapanış (TRY) serisi — grafik için; {@code from}–{@code to} aralığı.
     */
    public List<ManualChartPoint> loadDailyCloseSeriesTry(AssetType type, String symbol, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        LocalDate today = LocalDate.now(TZ);
        LocalDate end = to.isAfter(today) ? today : to;
        int maxLb = Math.max(1, resolveProperties.getMaxLookbackDays());
        long spanDays = ChronoUnit.DAYS.between(from, end) + 2;
        int allowed = AllowedHistoryDays.smallestCovering(spanDays + maxLb);
        if (allowed < 0) {
            return List.of();
        }
        LatestPricingSnapshot snap = marketDataClient.loadLatestPricing();
        List<MarketPriceHistoryDto> raw = loadHistory(type, symbol, from, end, maxLb, allowed);
        NavigableMap<LocalDate, BigDecimal> byDay = aggregateLastPricePerDay(type, symbol, raw, snap);
        List<ManualChartPoint> out = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> e : byDay.entrySet()) {
            if (e.getKey().isBefore(from) || e.getKey().isAfter(end)) {
                continue;
            }
            out.add(new ManualChartPoint(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparing(ManualChartPoint::date));
        return out;
    }

    private List<MarketPriceHistoryDto> loadHistory(
            AssetType type,
            String symbol,
            LocalDate requestedFrom,
            LocalDate today,
            int maxLb,
            int allowedDays
    ) {
        LocalDate from = requestedFrom.minusDays(maxLb + 2);
        if (from.isAfter(today)) {
            return List.of();
        }
        return switch (type) {
            case METAL -> marketDataClient.getMetalHistoryBetween(symbol, from, today);
            case BIST -> {
                String key = symbol.trim().toUpperCase();
                Map<String, List<MarketPriceHistoryDto>> m =
                        marketDataClient.getBistBatchHistoryMapped(key, from, today);
                yield m.getOrDefault(key, List.of());
            }
            case STOCK -> {
                if (isBistSymbol(symbol)) {
                    Map<String, List<MarketPriceHistoryDto>> m =
                            marketDataClient.getBistBatchHistoryMapped(symbol.trim().toUpperCase(), from, today);
                    String key = symbol.trim().toUpperCase();
                    yield m.getOrDefault(key, m.values().stream().findFirst().orElse(List.of()));
                }
                yield marketDataClient.getHistory(type, symbol, allowedDays);
            }
            default -> marketDataClient.getHistory(type, symbol, allowedDays);
        };
    }

    private static boolean isBistSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        String s = symbol.trim().toUpperCase();
        return s.endsWith(".IS");
    }

    private NavigableMap<LocalDate, BigDecimal> aggregateLastPricePerDay(
            AssetType type,
            String symbol,
            List<MarketPriceHistoryDto> raw,
            LatestPricingSnapshot snap
    ) {
        TreeMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (raw == null) {
            return map;
        }
        List<MarketPriceHistoryDto> sorted = raw.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (MarketPriceHistoryDto row : sorted) {
            if (row.timestamp() == null) {
                continue;
            }
            BigDecimal px = rowToTry(type, symbol, row, snap);
            if (px == null || px.signum() <= 0) {
                continue;
            }
            LocalDate d = row.timestamp().atZone(TZ).toLocalDate();
            map.put(d, px);
        }
        return map;
    }

    private static BigDecimal rowToTry(AssetType type, String symbol, MarketPriceHistoryDto row, LatestPricingSnapshot snap) {
        BigDecimal raw = mid(row);
        if (raw == null || raw.signum() <= 0) {
            return null;
        }
        if (type == AssetType.STOCK && isBistSymbol(symbol)) {
            return raw;
        }
        if (type == AssetType.BIST) {
            return raw;
        }
        return switch (type) {
            case CRYPTO, FUND, STOCK -> usdToTry(raw, snap);
            default -> raw;
        };
    }

    private static BigDecimal mid(MarketPriceHistoryDto r) {
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

    private static BigDecimal usdToTry(BigDecimal usdPrice, LatestPricingSnapshot snap) {
        if (usdPrice == null) {
            return null;
        }
        if (snap == null) {
            return usdPrice;
        }
        var usdTry = snap.fx().get("USDTRY");
        if (usdTry == null || usdTry.buyPrice() == null || usdTry.buyPrice().signum() <= 0) {
            return usdPrice;
        }
        return usdPrice.multiply(usdTry.buyPrice()).setScale(8, RoundingMode.HALF_UP);
    }

    public record ManualChartPoint(LocalDate date, BigDecimal priceTry) {
    }
}
