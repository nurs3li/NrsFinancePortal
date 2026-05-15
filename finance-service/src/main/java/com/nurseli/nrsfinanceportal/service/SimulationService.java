package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.SimulationResponseDto;
import com.nurseli.nrsfinanceportal.common.dto.SimulationPerformancePointDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SimulationService {

    /**
     * USD cinsinden hisse/kripto/fon geçmiş serisi TRY'ye çevrilirken kullanıcıya gösterilecek kısa uyarı kodu
     * (frontend i18n anahtarı ile eşlenir). TRY kotasyonlu varlıklar (FX, METAL, BIST) için kullanılmaz.
     */
    public static final String NOTICE_USD_DENOMINATED = "SIMULATION_USD_DENOMINATED";

    private final MarketDataClient marketDataClient;
    private final DateToDaysHelper dateToDaysHelper;

    @Transactional(readOnly = true)
    public SimulationResponseDto simulate(
            AssetType type,
            String rawSymbol,
            BigDecimal amountTry,
            LocalDate buyDate,
            BigDecimal manualBuyPriceTry
    ) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (amountTry == null || amountTry.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (buyDate == null) {
            throw new IllegalArgumentException("Buy date is required");
        }
        if (!buyDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Buy date must be in the past");
        }

        String symbol = SymbolNormalizer.normalize(type, rawSymbol.trim().toUpperCase());
        // Add a small buffer to avoid boundary misses from provider/day-cutoff behavior.
        int days = Math.min(dateToDaysHelper.toDays(buyDate) + 7, 3650);
        LocalDate today = LocalDate.now();

        List<MarketPriceHistoryDto> history;
        if (type == AssetType.BIST) {
            LocalDate from = buyDate.minusDays(30);
            history = marketDataClient.getBistHistoryBetween(symbol, from, today);
        } else {
            history = marketDataClient.getHistory(type, symbol, days);
        }
        if (history == null) {
            history = List.of();
        }

        BigDecimal spotUsdTry = resolveUsdTryRate();
        List<MarketPriceHistoryDto> usdTryHistory = List.of();
        if (needsHistoricalUsdTrySeries(type)) {
            List<MarketPriceHistoryDto> fx = marketDataClient.getHistory(AssetType.FX, "USDTRY", days);
            usdTryHistory = fx != null ? fx : List.of();
        }

        List<MarketPriceHistoryDto> normalizedHistory =
                normalizeHistoryPricesToTry(history, type, usdTryHistory, spotUsdTry);
        BigDecimal currentPrice = nz(marketDataClient.getPriceTry(type, symbol));
        if (currentPrice.signum() <= 0) {
            throw new IllegalStateException("Current price not found");
        }

        HistoricalPriceRef historicalRef;
        if (manualBuyPriceTry != null && manualBuyPriceTry.signum() > 0) {
            historicalRef = new HistoricalPriceRef(
                    manualBuyPriceTry,
                    buyDate,
                    "USER_INPUT",
                    "EXACT"
            );
        } else if (!normalizedHistory.isEmpty()) {
            historicalRef = resolveHistoricalPriceAtOrBeforeDate(normalizedHistory, buyDate);
        } else {
            // History may be temporarily missing for some symbols although latest quote exists.
            // In that case, allow simulation with latest price fallback instead of hard-failing.
            historicalRef = new HistoricalPriceRef(
                    currentPrice,
                    buyDate,
                    "SYSTEM_LATEST_FALLBACK",
                    "FALLBACK"
            );
        }
        BigDecimal historicalPrice = historicalRef.priceTry();

        BigDecimal units = amountTry.divide(historicalPrice, 8, RoundingMode.HALF_UP);
        BigDecimal currentValue = units.multiply(currentPrice);
        BigDecimal pnl = currentValue.subtract(amountTry);

        BigDecimal pnlPct = amountTry.signum() == 0
                ? BigDecimal.ZERO
                : pnl.divide(amountTry, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        String message = "%s tarihinde %s TRY %s yatırımı bugün %s TRY olurdu. (alış: %s, kaynak: %s)"
                .formatted(
                        buyDate,
                        amountTry.stripTrailingZeros().toPlainString(),
                        symbol,
                        currentValue.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        historicalRef.priceDate(),
                        historicalRef.source()
                );

        List<SimulationPerformancePointDto> performanceSeries = buildPerformanceSeries(
                normalizedHistory,
                buyDate,
                historicalPrice,
                currentPrice
        );

        String approximationNoticeCode = needsHistoricalUsdTrySeries(type) ? NOTICE_USD_DENOMINATED : null;

        return new SimulationResponseDto(
                type.name(),
                symbol,
                buyDate,
                amountTry,
                historicalPrice,
                currentPrice,
                units,
                currentValue,
                pnl,
                pnlPct,
                historicalRef.source(),
                historicalRef.priceDate(),
                historicalRef.qualityFlag(),
                performanceSeries,
                approximationNoticeCode,
                message
        );
    }

    /**
     * "Nearest" yerine güvenli kural:
     * - Önce buyDate'e eşit kayıt
     * - Yoksa buyDate'den önceki en yakın kayıt (forward lookup yok)
     */
    private HistoricalPriceRef resolveHistoricalPriceAtOrBeforeDate(
            List<MarketPriceHistoryDto> history,
            LocalDate buyDate
    ) {
        MarketPriceHistoryDto row = history.stream()
                .filter(h -> h.timestamp() != null)
                .filter(h -> !h.timestamp().toLocalDate().isAfter(buyDate))
                .max(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .orElse(null);

        if (row == null) {
            // If there is no point at/before the selected date, fallback to earliest available point.
            row = history.stream()
                    .filter(h -> h.timestamp() != null)
                    .min(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                    .orElseThrow(() -> new IllegalStateException("No historical point available for selected symbol"));
        }

        BigDecimal price = midPrice(row);
        if (price == null || price.signum() <= 0) {
            throw new IllegalStateException("Historical price is invalid");
        }
        String quality;
        if (row.timestamp().toLocalDate().isEqual(buyDate)) {
            quality = "EXACT";
        } else if (row.timestamp().toLocalDate().isBefore(buyDate)) {
            quality = "PREVIOUS_DAY";
        } else {
            quality = "FALLBACK";
        }
        return new HistoricalPriceRef(price, row.timestamp().toLocalDate(), "SYSTEM_HISTORY", quality);
    }

    /**
     * MarketDashboardService'teki geçmiş veri yaklaşımıyla uyumlu:
     * geçmiş fiyat noktalarını tarihe göre sıralar ve alış fiyatına göre kümülatif getiri (%) üretir.
     */
    private List<SimulationPerformancePointDto> buildPerformanceSeries(
            List<MarketPriceHistoryDto> history,
            LocalDate buyDate,
            BigDecimal buyPriceTry,
            BigDecimal currentPriceTry
    ) {
        BigDecimal safeBuy = nz(buyPriceTry);
        if (safeBuy.signum() <= 0) {
            return List.of();
        }

        List<SimulationPerformancePointDto> points = history.stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .filter(h -> !h.timestamp().toLocalDate().isBefore(buyDate))
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .map(h -> toPoint(h.timestamp(), midPrice(h), safeBuy))
                .filter(Objects::nonNull)
                .toList();

        LocalDate today = LocalDate.now();
        BigDecimal todayPct = currentPriceTry.subtract(safeBuy)
                .divide(safeBuy, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        SimulationPerformancePointDto todayPoint =
                new SimulationPerformancePointDto(today, currentPriceTry, todayPct);

        if (points.isEmpty()) {
            return List.of(
                    new SimulationPerformancePointDto(buyDate, safeBuy, BigDecimal.ZERO),
                    todayPoint
            );
        }

        SimulationPerformancePointDto last = points.get(points.size() - 1);
        // Günlük mum son noktası bugün olsa bile alış anındaki kapanıştan farklı olabilir; canlı kotasyonu yansıt.
        if (last.date().equals(today)) {
            ArrayList<SimulationPerformancePointDto> head =
                    new ArrayList<>(points.subList(0, points.size() - 1));
            head.add(todayPoint);
            return List.copyOf(head);
        }
        if (today.isAfter(last.date())) {
            return java.util.stream.Stream.concat(points.stream(), java.util.stream.Stream.of(todayPoint)).toList();
        }
        return points;
    }

    private SimulationPerformancePointDto toPoint(LocalDateTime ts, BigDecimal priceTry, BigDecimal buyPriceTry) {
        if (priceTry == null || priceTry.signum() <= 0) {
            return null;
        }
        BigDecimal pct = priceTry.subtract(buyPriceTry)
                .divide(buyPriceTry, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        return new SimulationPerformancePointDto(ts.toLocalDate(), priceTry, pct);
    }

    private BigDecimal midPrice(MarketPriceHistoryDto row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal buy = row.buyPrice();
        BigDecimal sell = row.sellPrice();
        if (buy != null && buy.signum() > 0 && sell != null && sell.signum() > 0) {
            return buy.add(sell).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        }
        if (buy != null && buy.signum() > 0) {
            return buy;
        }
        if (sell != null && sell.signum() > 0) {
            return sell;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** USD kotasyonlu geçmiş: STOCK, CRYPTO, FUND — tarihsel USDTRY ile çarpılır. BIST/FX/METAL zaten TRY veya ayrı mantık. */
    private static boolean needsHistoricalUsdTrySeries(AssetType type) {
        return type == AssetType.STOCK || type == AssetType.CRYPTO || type == AssetType.FUND;
    }

    private BigDecimal resolveUsdTryRate() {
        BigDecimal rate = nz(marketDataClient.getPriceTry(AssetType.FX, "USDTRY"));
        if (rate.signum() <= 0) {
            // Keep backward compatibility: if FX feed is unavailable, assume already-TRY history.
            return BigDecimal.ONE;
        }
        return rate;
    }

    /**
     * USD kotasyonlu varlık geçmişini TRY'ye çevirir.
     * <p><b>STOCK / CRYPTO / FUND:</b> market-data günlük USDTRY geçmişi çekilir; her varlık mumunun {@code timestamp}
     * değeri için, aynı veya önceki zamandaki son USDTRY kapanışı (mid) çarpan olarak kullanılır
     * ({@code usdTry.timestamp <= asset.timestamp}). Günlük mumlar genelde gün başına hizalı {@code LocalDateTime}
     * taşır; kural bu yüzden takvim günü ile tutarlıdır.</p>
     * <p>Eksik kur: önce tüm USDTRY noktaları varlık zamanından sonraysa serinin en erken kuru; hâlâ geçersizse
     * güncel spot {@code spotUsdTryFallback}; o da yoksa {@code 1} (geriye dönük).</p>
     * <p><b>BIST / FX / METAL:</b> Geçmiş zaten TRY (veya FX için kur birimi); bu metotta değiştirilmez.</p>
     */
    private List<MarketPriceHistoryDto> normalizeHistoryPricesToTry(
            List<MarketPriceHistoryDto> history,
            AssetType type,
            List<MarketPriceHistoryDto> usdTryHistory,
            BigDecimal spotUsdTryFallback
    ) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (!needsHistoricalUsdTrySeries(type)) {
            return history;
        }

        List<MarketPriceHistoryDto> fxSorted = (usdTryHistory == null ? List.<MarketPriceHistoryDto>of() : usdTryHistory)
                .stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .toList();

        return history.stream()
                .filter(Objects::nonNull)
                .map(h -> {
                    BigDecimal rate = resolveUsdTryAt(h.timestamp(), fxSorted, spotUsdTryFallback);
                    return new MarketPriceHistoryDto(
                            nz(h.buyPrice()).multiply(rate),
                            nz(h.sellPrice()).multiply(rate),
                            h.timestamp()
                    );
                })
                .toList();
    }

    /**
     * Son USDTRY mid değeri: {@code fxSorted} artan sırada; {@code assetTs} için
     * {@code fx.timestamp <= assetTs} koşulunu sağlayan en son kayıt.
     */
    private BigDecimal resolveUsdTryAt(
            LocalDateTime assetTs,
            List<MarketPriceHistoryDto> fxSorted,
            BigDecimal spotUsdTryFallback
    ) {
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
        BigDecimal earliest = midPrice(fxSorted.get(0));
        if (earliest != null && earliest.signum() > 0) {
            return earliest;
        }
        return positiveOrOne(spotUsdTryFallback);
    }

    private static BigDecimal positiveOrOne(BigDecimal spot) {
        if (spot != null && spot.signum() > 0) {
            return spot;
        }
        return BigDecimal.ONE;
    }

    private record HistoricalPriceRef(
            BigDecimal priceTry,
            LocalDate priceDate,
            String source,
            String qualityFlag
    ) {
    }
}
