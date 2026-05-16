package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.SimulationResponseDto;
import com.nurseli.nrsfinanceportal.common.dto.SimulationPerformancePointDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.asset.SimulationDisplayCurrency;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    /**
     * USD cinsinden hisse/kripto/fon geçmiş serisi TRY'ye çevrilirken kullanıcıya gösterilecek kısa uyarı kodu
     * (frontend i18n anahtarı ile eşlenir). TRY kotasyonlu varlıklar (FX, METAL, BIST) için kullanılmaz.
     */
    public static final String NOTICE_USD_DENOMINATED = "SIMULATION_USD_DENOMINATED";

    /** Seçilen tarihte veya öncesinde sistem fiyatı yok; istemci manuel birim fiyat istemeli. */
    public static final String MANUAL_PRICE_REQUIRED = "MANUAL_PRICE_REQUIRED";

    private final MarketDataClient marketDataClient;
    private final DateToDaysHelper dateToDaysHelper;

    @Transactional(readOnly = true)
    public SimulationResponseDto simulate(
            AssetType type,
            String rawSymbol,
            BigDecimal amount,
            LocalDate buyDate,
            BigDecimal manualBuyPrice
    ) {
        return simulate(type, rawSymbol, amount, buyDate, manualBuyPrice, SimulationDisplayCurrency.TRY);
    }

    @Transactional(readOnly = true)
    public SimulationResponseDto simulate(
            AssetType type,
            String rawSymbol,
            BigDecimal amount,
            LocalDate buyDate,
            BigDecimal manualBuyPrice,
            SimulationDisplayCurrency displayCurrency
    ) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        SimulationDisplayCurrency currency = displayCurrency == null ? SimulationDisplayCurrency.TRY : displayCurrency;
        if (buyDate == null) {
            throw new IllegalArgumentException("Buy date is required");
        }
        LocalDate today = todayIstanbul();
        if (buyDate.isAfter(today)) {
            throw new IllegalArgumentException("Buy date cannot be in the future");
        }

        String symbol = SymbolNormalizer.normalize(type, rawSymbol.trim().toUpperCase());
        // Add a small buffer to avoid boundary misses from provider/day-cutoff behavior.
        int days = Math.min(dateToDaysHelper.toDays(buyDate) + 7, 3650);
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
        if (needsHistoricalUsdTrySeries(type) || currency == SimulationDisplayCurrency.USD) {
            List<MarketPriceHistoryDto> fx = marketDataClient.getHistory(AssetType.FX, "USDTRY", days);
            usdTryHistory = fx != null ? fx : List.of();
        }
        List<MarketPriceHistoryDto> fxSorted = usdTryHistory.stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .toList();

        BigDecimal amountTry = amount;
        BigDecimal manualBuyPriceTry = manualBuyPrice;
        if (currency == SimulationDisplayCurrency.USD) {
            BigDecimal usdTryAtBuy = resolveUsdTryAt(buyDate.atStartOfDay(), fxSorted, spotUsdTry);
            amountTry = amount.multiply(usdTryAtBuy);
            if (manualBuyPrice != null && manualBuyPrice.signum() > 0) {
                manualBuyPriceTry = manualBuyPrice.multiply(usdTryAtBuy);
            }
        }

        List<MarketPriceHistoryDto> normalizedHistory =
                normalizeHistoryPricesToTry(history, type, usdTryHistory, spotUsdTry);
        BigDecimal currentPrice = resolveCurrentPriceTry(type, symbol, normalizedHistory);
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
            String unit = currency == SimulationDisplayCurrency.USD ? "USD/birim" : "TRY/birim";
            throw new IllegalArgumentException(
                    MANUAL_PRICE_REQUIRED + ": Seçilen tarih için geçmiş fiyat bulunamadı. Lütfen o güne ait alış fiyatını (" + unit + ") girin."
            );
        }
        BigDecimal historicalPriceTry = historicalRef.priceTry();

        BigDecimal units = amountTry.divide(historicalPriceTry, 8, RoundingMode.HALF_UP);
        BigDecimal currentValueTry = units.multiply(currentPrice);
        BigDecimal pnlTry = currentValueTry.subtract(amountTry);

        BigDecimal pnlPct = amountTry.signum() == 0
                ? BigDecimal.ZERO
                : pnlTry.divide(amountTry, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        List<SimulationPerformancePointDto> performanceSeriesTry = buildPerformanceSeries(
                normalizedHistory,
                buyDate,
                historicalPriceTry,
                currentPrice
        );

        String approximationNoticeCode = needsHistoricalUsdTrySeries(type) ? NOTICE_USD_DENOMINATED : null;

        if (currency == SimulationDisplayCurrency.TRY) {
            String message = "%s tarihinde %s TRY %s yatırımı bugün %s TRY olurdu. (alış: %s, kaynak: %s)"
                    .formatted(
                            buyDate,
                            amount.stripTrailingZeros().toPlainString(),
                            symbol,
                            currentValueTry.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                            historicalRef.priceDate(),
                            historicalRef.source()
                    );
            return new SimulationResponseDto(
                    type.name(),
                    symbol,
                    buyDate,
                    amount,
                    historicalPriceTry,
                    currentPrice,
                    units,
                    currentValueTry,
                    pnlTry,
                    pnlPct,
                    historicalRef.source(),
                    historicalRef.priceDate(),
                    historicalRef.qualityFlag(),
                    performanceSeriesTry,
                    approximationNoticeCode,
                    message,
                    SimulationDisplayCurrency.TRY.name()
            );
        }

        BigDecimal historicalPriceUsd = toUsd(historicalPriceTry, historicalRef.priceDate(), fxSorted, spotUsdTry);
        BigDecimal currentPriceUsd = toUsd(currentPrice, today, fxSorted, spotUsdTry);
        BigDecimal currentValueUsd = units.multiply(currentPriceUsd);
        BigDecimal pnlUsd = currentValueUsd.subtract(amount);

        List<SimulationPerformancePointDto> performanceSeriesUsd = performanceSeriesTry.stream()
                .map(p -> new SimulationPerformancePointDto(
                        p.date(),
                        toUsd(p.priceTry(), p.date(), fxSorted, spotUsdTry),
                        p.cumulativeReturnPct()
                ))
                .toList();

        String message = "%s tarihinde %s USD %s yatırımı bugün %s USD olurdu. (alış: %s, kaynak: %s)"
                .formatted(
                        buyDate,
                        amount.stripTrailingZeros().toPlainString(),
                        symbol,
                        currentValueUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        historicalRef.priceDate(),
                        historicalRef.source()
                );

        return new SimulationResponseDto(
                type.name(),
                symbol,
                buyDate,
                amount,
                historicalPriceUsd,
                currentPriceUsd,
                units,
                currentValueUsd,
                pnlUsd,
                pnlPct,
                historicalRef.source(),
                historicalRef.priceDate(),
                historicalRef.qualityFlag(),
                performanceSeriesUsd,
                approximationNoticeCode,
                message,
                SimulationDisplayCurrency.USD.name()
        );
    }

    private BigDecimal toUsd(
            BigDecimal tryAmount,
            LocalDate date,
            List<MarketPriceHistoryDto> fxSorted,
            BigDecimal spotUsdTryFallback
    ) {
        if (tryAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = resolveUsdTryAt(date.atStartOfDay(), fxSorted, spotUsdTryFallback);
        if (rate.signum() <= 0) {
            return tryAmount;
        }
        return tryAmount.divide(rate, 8, RoundingMode.HALF_UP);
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
            throw new IllegalArgumentException(
                    MANUAL_PRICE_REQUIRED + ": Seçilen tarih veya öncesinde fiyat yok. İlk geçmiş veri daha sonraki bir günde; lütfen alım günü için manuel fiyat girin."
            );
        }

        BigDecimal price = midPrice(row);
        if (price == null || price.signum() <= 0) {
            throw new IllegalStateException("Historical price is invalid");
        }
        String quality = row.timestamp().toLocalDate().isEqual(buyDate) ? "EXACT" : "PREVIOUS_DAY";
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

        // Alım tarihinden itibaren yalnızca veri olan işlem günleri (gün başına tek mum).
        Map<LocalDate, MarketPriceHistoryDto> dayCandles = new LinkedHashMap<>();
        history.stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .filter(h -> !h.timestamp().toLocalDate().isBefore(buyDate))
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .forEach(h -> dayCandles.put(h.timestamp().toLocalDate(), h));

        List<SimulationPerformancePointDto> points = dayCandles.values().stream()
                .map(h -> toPoint(h.timestamp(), midPrice(h), safeBuy))
                .filter(Objects::nonNull)
                .toList();

        ArrayList<SimulationPerformancePointDto> series = new ArrayList<>(points);
        if (series.isEmpty()) {
            series.add(new SimulationPerformancePointDto(buyDate, safeBuy, BigDecimal.ZERO));
        } else if (series.get(0).date().isAfter(buyDate)) {
            series.add(0, new SimulationPerformancePointDto(buyDate, safeBuy, BigDecimal.ZERO));
        } else if (series.get(0).date().isEqual(buyDate)
                && series.get(0).priceTry().compareTo(safeBuy) != 0) {
            series.set(0, new SimulationPerformancePointDto(buyDate, safeBuy, BigDecimal.ZERO));
        }

        LocalDate today = todayIstanbul();
        BigDecimal todayPct = currentPriceTry.subtract(safeBuy)
                .divide(safeBuy, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        SimulationPerformancePointDto todayPoint =
                new SimulationPerformancePointDto(today, currentPriceTry, todayPct);

        if (series.isEmpty()) {
            return List.of(
                    new SimulationPerformancePointDto(buyDate, safeBuy, BigDecimal.ZERO),
                    todayPoint
            );
        }

        SimulationPerformancePointDto last = series.get(series.size() - 1);
        // Günlük mum son noktası bugün olsa bile alış anındaki kapanıştan farklı olabilir; canlı kotasyonu yansıt.
        if (last.date().equals(today)) {
            ArrayList<SimulationPerformancePointDto> head =
                    new ArrayList<>(series.subList(0, series.size() - 1));
            head.add(todayPoint);
            return List.copyOf(head);
        }
        if (today.isAfter(last.date())) {
            series.add(todayPoint);
        }
        return List.copyOf(series);
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

    private static LocalDate todayIstanbul() {
        return LocalDate.now(TZ);
    }

    /**
     * Canlı kotasyon yoksa (ör. BIST latest listesinde sembol yok) geçmiş serinin son geçerli gününü kullan.
     */
    private BigDecimal resolveCurrentPriceTry(
            AssetType type,
            String symbol,
            List<MarketPriceHistoryDto> normalizedHistory
    ) {
        BigDecimal spot = nz(marketDataClient.getPriceTry(type, symbol));
        if (spot.signum() > 0) {
            return spot;
        }
        if (normalizedHistory == null || normalizedHistory.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return normalizedHistory.stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .max(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .map(this::midPrice)
                .filter(p -> p != null && p.signum() > 0)
                .orElse(BigDecimal.ZERO);
    }

    private record HistoricalPriceRef(
            BigDecimal priceTry,
            LocalDate priceDate,
            String source,
            String qualityFlag
    ) {
    }
}
