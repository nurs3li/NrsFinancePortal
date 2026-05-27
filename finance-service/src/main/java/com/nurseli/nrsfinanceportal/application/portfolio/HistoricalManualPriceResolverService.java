package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.ManualPriceResolveDto;
import com.nurseli.nrsfinanceportal.config.ManualPortfolioPriceResolveProperties;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
import java.util.function.Supplier;

/**
 * finance-service tarihsel manuel fiyat çözümleyici — manuel portfolio için geçmiş kapanış fiyatı ve TRY serisi üretir.
 */
@RequiredArgsConstructor
@Service

public class HistoricalManualPriceResolverService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int HISTORY_BUFFER_DAYS = 2;
    private static final int HOURLY_EXACT_MAX_AGE_DAYS = 30;

    private final MarketDataClient marketDataClient;
    private final ManualPortfolioPriceResolveProperties resolveProperties;

    /**
     * {@code resolve} — Varlık türü, sembol ve tarih için exact/previous/next eşleşmeli fiyat çözümlemesi yapar.
     */
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

        List<MarketPriceHistoryDto> raw = loadHistory(type, symbol, requestedDate, today, maxLb, allowed);
        FxContext fx = loadFxContext(type, symbol, allowed, snap);
        NavigableMap<LocalDate, BigDecimal> byDay = aggregateLastPricePerDay(type, symbol, raw, fx);
        if (byDay.isEmpty()) {
            return ManualPriceResolveDto.notFound(type, symbol, requestedDate,
                    "Seçilen tarih için fiyat bulunamadı. Manuel fiyat girin.");
        }

        BigDecimal exactPx = byDay.get(requestedDate);
        if (exactPx != null) {
            return ManualPriceResolveDto.ok(type, symbol, requestedDate, requestedDate, exactPx,
                    ManualPriceSource.MARKET_HISTORY_EXACT, "TRY", null);
        }

        BigDecimal sameDayHourly = resolveSameDayHourlyPriceTry(type, symbol, requestedDate, today, fx);
        if (sameDayHourly != null && sameDayHourly.signum() > 0) {
            return ManualPriceResolveDto.ok(type, symbol, requestedDate, requestedDate, sameDayHourly,
                    ManualPriceSource.MARKET_HISTORY_SAME_DAY_HOURLY, "TRY",
                    "Seçilen gün için günlük kapanış yerine aynı günün son saatlik fiyatı kullanıldı.");
        }

        LocalDate lower = requestedDate.minusDays(maxLb);
        Map.Entry<LocalDate, BigDecimal> previous = byDay.floorEntry(requestedDate.minusDays(1));
        if (previous != null
                && previous.getKey() != null
                && !previous.getKey().isBefore(lower)
                && previous.getValue() != null
                && previous.getValue().signum() > 0) {
            return ManualPriceResolveDto.ok(type, symbol, requestedDate, previous.getKey(), previous.getValue(),
                    ManualPriceSource.MARKET_HISTORY_PREVIOUS_CLOSE, "TRY",
                    "Seçilen tarihte fiyat bulunamadı; en yakın önceki kapanış kullanıldı.");
        }

        LocalDate upper = requestedDate.plusDays(maxLb);
        Map.Entry<LocalDate, BigDecimal> next = byDay.ceilingEntry(requestedDate.plusDays(1));
        if (next != null
                && next.getKey() != null
                && !next.getKey().isAfter(upper)
                && next.getValue() != null
                && next.getValue().signum() > 0) {
            return ManualPriceResolveDto.ok(type, symbol, requestedDate, next.getKey(), next.getValue(),
                    ManualPriceSource.MARKET_HISTORY_NEXT_CLOSE, "TRY",
                    "Seçilen tarihte fiyat bulunamadı; en yakın sonraki kapanış kullanıldı.");
        }

        return ManualPriceResolveDto.notFound(type, symbol, requestedDate,
                "Seçilen tarih için fiyat bulunamadı. Manuel fiyat girin.");
    }

    /**
     * {@code loadDailyCloseSeriesTry} — Belirtilen tarih aralığında günlük TRY kapanış fiyat serisini yükler.
     */
    public List<ManualChartPoint> loadDailyCloseSeriesTry(AssetType type, String symbol, LocalDate from, LocalDate to) {
        if (type == null || symbol == null || symbol.isBlank() || from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        LocalDate today = LocalDate.now(TZ);
        LocalDate end = to.isAfter(today) ? today : to;
    int maxLb = Math.max(1, resolveProperties.getMaxLookbackDays());
        long spanDays = ChronoUnit.DAYS.between(from, end) + 2;
        int allowed = AllowedHistoryDays.smallestCovering(spanDays + maxLb);
        LatestPricingSnapshot snap = marketDataClient.loadLatestPricing();
        List<MarketPriceHistoryDto> raw = loadHistory(type, symbol, from, end, maxLb, allowed);
        FxContext fx = loadFxContext(type, symbol, allowed, snap);
        NavigableMap<LocalDate, BigDecimal> byDay = aggregateLastPricePerDay(type, symbol, raw, fx);
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

    private FxContext loadFxContext(AssetType type, String symbol, int allowedDays, LatestPricingSnapshot snap) {
        BigDecimal spot = HistoricalUsdTryConversion.spotUsdTry(snap);
        if (!HistoricalUsdTryConversion.needsHistoricalUsdTry(type, symbol)
                || (type == AssetType.FUND && marketDataClient.isTryQuotedFund(symbol, snap))) {
            return new FxContext(List.of(), spot);
        }
        List<MarketPriceHistoryDto> fxRaw = marketDataClient.getHistory(AssetType.FX, "USDTRY", allowedDays);
        return new FxContext(HistoricalUsdTryConversion.sortFxHistory(fxRaw), spot);
    }

    private List<MarketPriceHistoryDto> loadHistory(
            AssetType type,
            String symbol,
            LocalDate requestedFrom,
            LocalDate today,
            int maxLb,
            int allowedDays
    ) {
        LocalDate from = requestedFrom.minusDays(maxLb + HISTORY_BUFFER_DAYS);
        if (from.isAfter(today)) {
            return List.of();
        }
        return switch (type) {
            case METAL -> loadChartCompatibleOrFallback(type, symbol, allowedDays,
                    () -> marketDataClient.getMetalHistoryBetween(symbol, from, today));
            case BIST -> {
                String key = stripBistSuffix(symbol);
                Map<String, List<MarketPriceHistoryDto>> m =
                        marketDataClient.getBistBatchHistoryMapped(key, from, today);
                yield m.getOrDefault(key, List.of());
            }
            case STOCK -> {
                if (HistoricalUsdTryConversion.isBistSymbol(symbol)) {
                    String key = stripBistSuffix(symbol);
                    Map<String, List<MarketPriceHistoryDto>> m =
                            marketDataClient.getBistBatchHistoryMapped(key, from, today);
                    yield m.getOrDefault(key, m.values().stream().findFirst().orElse(List.of()));
                }
                yield loadChartCompatibleOrFallback(type, symbol, allowedDays,
                        () -> marketDataClient.getHistory(type, symbol, allowedDays));
            }
            case FX, CRYPTO -> loadChartCompatibleOrFallback(type, symbol, allowedDays,
                    () -> marketDataClient.getHistory(type, symbol, allowedDays));
            default -> marketDataClient.getHistory(type, symbol, allowedDays);
        };
    }

    private static String stripBistSuffix(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        return normalized.endsWith(".IS") ? normalized.substring(0, normalized.length() - 3) : normalized;
    }

    private List<MarketPriceHistoryDto> loadChartCompatibleOrFallback(
            AssetType type,
            String symbol,
            int allowedDays,
            Supplier<List<MarketPriceHistoryDto>> fallbackSupplier
    ) {
        List<MarketPriceHistoryDto> chartCompatible = marketDataClient.getChartCompatibleHistory(type, symbol, allowedDays, "daily");
        if (chartCompatible != null && !chartCompatible.isEmpty()) {
            return chartCompatible;
        }
        List<MarketPriceHistoryDto> fallback = fallbackSupplier != null ? fallbackSupplier.get() : null;
        return fallback != null ? fallback : List.of();
    }

    private BigDecimal resolveSameDayHourlyPriceTry(
            AssetType type,
            String symbol,
            LocalDate requestedDate,
            LocalDate today,
            FxContext fx
    ) {
        if (!supportsHourlyExactLookup(type, symbol)) {
            return null;
        }
        long ageDays = ChronoUnit.DAYS.between(requestedDate, today);
        if (ageDays < 0 || ageDays > HOURLY_EXACT_MAX_AGE_DAYS) {
            return null;
        }
        int hourlyDays = AllowedHistoryDays.smallestCovering(ageDays + 1);
        List<MarketPriceHistoryDto> hourly = marketDataClient.getChartCompatibleHistory(type, symbol, hourlyDays, "hourly");
        return hourly.stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .filter(h -> h.timestamp().atZone(TZ).toLocalDate().isEqual(requestedDate))
                .max(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .map(row -> rowToTry(type, symbol, row, fx))
                .filter(px -> px != null && px.signum() > 0)
                .orElse(null);
    }

    private static boolean supportsHourlyExactLookup(AssetType type, String symbol) {
        if (type == AssetType.STOCK && HistoricalUsdTryConversion.isBistSymbol(symbol)) {
            return false;
        }
        return type == AssetType.FX || type == AssetType.CRYPTO || type == AssetType.STOCK || type == AssetType.METAL;
    }

    private NavigableMap<LocalDate, BigDecimal> aggregateLastPricePerDay(
            AssetType type,
            String symbol,
            List<MarketPriceHistoryDto> raw,
            FxContext fx
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
            BigDecimal px = rowToTry(type, symbol, row, fx);
            if (px == null || px.signum() <= 0) {
                continue;
            }
            LocalDate d = row.timestamp().atZone(TZ).toLocalDate();
            map.put(d, px);
        }
        return map;
    }

    private BigDecimal rowToTry(AssetType type, String symbol, MarketPriceHistoryDto row, FxContext fx) {
        BigDecimal raw = HistoricalUsdTryConversion.mid(row);
        if (raw == null || raw.signum() <= 0) {
            return null;
        }
        if (type == AssetType.STOCK && HistoricalUsdTryConversion.isBistSymbol(symbol)) {
            return raw;
        }
        if (type == AssetType.BIST) {
            return raw;
        }
        if (type == AssetType.FUND && marketDataClient.isTryQuotedFund(symbol)) {
            return raw;
        }
        if (HistoricalUsdTryConversion.needsHistoricalUsdTry(type, symbol)) {
            return HistoricalUsdTryConversion.usdToTryAt(
                    raw, row.timestamp(), fx.fxSorted(), fx.spotUsdTry());
        }
        return raw;
    }

    private record FxContext(List<MarketPriceHistoryDto> fxSorted, BigDecimal spotUsdTry) {
    }
    /**
     * ManualChartPoint — Manuel portfolio grafik noktası — tarih ve TRY kapanış fiyatını taşır.
     */
    public record ManualChartPoint(LocalDate date, BigDecimal priceTry) {
    }
}
