package com.nurseli.nrsfinanceportal.application.simulation;

import com.nurseli.nrsfinanceportal.api.dto.SimulationResponseDto;
import com.nurseli.nrsfinanceportal.api.dto.SimulationPerformancePointDto;
import com.nurseli.nrsfinanceportal.application.portfolio.AllowedHistoryDays;
import com.nurseli.nrsfinanceportal.application.portfolio.HistoricalUsdTryConversion;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.asset.SimulationDisplayCurrency;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import com.nurseli.nrsfinanceportal.application.support.DateToDaysHelper;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * finance-service yatırım simülasyon servisi — geçmiş fiyatlarla belirli tarihte yapılmış yatırımın bugünkü değerini hesaplar.
 */
@RequiredArgsConstructor
@Service

public class SimulationService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int HISTORY_LOOKBACK_DAYS = 7;
    private static final int HISTORY_BUFFER_DAYS = 2;
    private static final int HISTORY_FORWARD_FALLBACK_DAYS = 7;
    private static final int HOURLY_EXACT_MAX_AGE_DAYS = 30;

    public static final String NOTICE_USD_DENOMINATED = "SIMULATION_USD_DENOMINATED";
    public static final String NOTICE_HISTORY_PREPARING = "SIMULATION_HISTORY_PREPARING";

    public static final String MANUAL_PRICE_REQUIRED = "MANUAL_PRICE_REQUIRED";

    private final MarketDataClient marketDataClient;
    private final DateToDaysHelper dateToDaysHelper;

    /**
     * {@code simulate} — Varlık türü, sembol, tutar ve alış tarihine göre birim, güncel değer, K/Z ve performans serisini TRY veya USD olarak simüle eder.
     */
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

    /**
     * {@code simulate} — Varlık türü, sembol, tutar ve alış tarihine göre birim, güncel değer, K/Z ve performans serisini TRY veya USD olarak simüle eder.
     */
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
        LocalDate historyFrom = buyDate.minusDays(HISTORY_LOOKBACK_DAYS + HISTORY_BUFFER_DAYS);
        long historySpanDays = ChronoUnit.DAYS.between(historyFrom, today) + 1;
        int days = AllowedHistoryDays.smallestCovering(Math.max(historySpanDays, dateToDaysHelper.toDays(historyFrom)));
        if (type == AssetType.CRYPTO && (manualBuyPrice == null || manualBuyPrice.signum() <= 0)) {
            LocalDate coverageTo = cryptoHistoryCoverageTo(historyFrom, today);
            MarketDataClient.CryptoHistoryCoverageDto coverage =
                    marketDataClient.getCryptoHistoryCoverage(symbol, historyFrom, coverageTo);
            if (coverage == null || !coverage.ready()) {
                marketDataClient.triggerCryptoHistoryWarmup(symbol, historyFrom, coverageTo, "simulation");
                String message = "Kripto geçmiş verisi hazırlanıyor. Birkaç dakika sonra tekrar deneyin.";
                return SimulationResponseDto.preparing(
                        type.name(),
                        symbol,
                        buyDate,
                        amount,
                        currency.name(),
                        message,
                        180
                );
            }
        }
        List<MarketPriceHistoryDto> history = loadHistory(type, symbol, historyFrom, today, days);
        if (history == null) {
            history = List.of();
        }

        BigDecimal spotUsdTry = resolveUsdTryRate();
        List<MarketPriceHistoryDto> usdTryHistory = List.of();
        if (needsHistoricalUsdTrySeries(type, symbol) || currency == SimulationDisplayCurrency.USD) {
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
                normalizeHistoryPricesToTry(history, type, symbol, usdTryHistory, spotUsdTry);
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
            historicalRef = resolveHistoricalPriceAtOrBeforeDate(
                    normalizedHistory,
                    type,
                    symbol,
                    buyDate,
                    today,
                    fxSorted,
                    spotUsdTry
            );
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

        String approximationNoticeCode = needsHistoricalUsdTrySeries(type, symbol) ? NOTICE_USD_DENOMINATED : null;

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

    private HistoricalPriceRef resolveHistoricalPriceAtOrBeforeDate(
            List<MarketPriceHistoryDto> history,
            AssetType type,
            String symbol,
            LocalDate buyDate,
            LocalDate today,
            List<MarketPriceHistoryDto> fxSorted,
            BigDecimal spotUsdTryFallback
    ) {
        MarketPriceHistoryDto exactRow = history.stream()
                .filter(h -> h.timestamp() != null)
                .filter(h -> h.timestamp().toLocalDate().isEqual(buyDate))
                .max(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .orElse(null);

        if (exactRow != null) {
            BigDecimal exactPrice = midPrice(exactRow);
            if (exactPrice == null || exactPrice.signum() <= 0) {
                throw new IllegalStateException("Historical exact price is invalid");
            }
            return new HistoricalPriceRef(exactPrice, buyDate, "SYSTEM_HISTORY", "EXACT");
        }

        BigDecimal sameDayHourly = resolveSameDayHourlyPriceTry(type, symbol, buyDate, today, fxSorted, spotUsdTryFallback);
        if (sameDayHourly != null && sameDayHourly.signum() > 0) {
            return new HistoricalPriceRef(sameDayHourly, buyDate, "SYSTEM_HISTORY", "EXACT_HOURLY");
        }

        MarketPriceHistoryDto row = history.stream()
                .filter(h -> h.timestamp() != null)
                .filter(h -> h.timestamp().toLocalDate().isBefore(buyDate))
                .max(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .orElse(null);

        if (row == null) {
            MarketPriceHistoryDto nextRow = history.stream()
                    .filter(h -> h.timestamp() != null)
                    .filter(h -> h.timestamp().toLocalDate().isAfter(buyDate))
                    .filter(h -> !h.timestamp().toLocalDate().isAfter(buyDate.plusDays(HISTORY_FORWARD_FALLBACK_DAYS)))
                    .min(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                    .orElse(null);
            if (nextRow != null) {
                BigDecimal nextPrice = midPrice(nextRow);
                if (nextPrice == null || nextPrice.signum() <= 0) {
                    throw new IllegalStateException("Historical fallback price is invalid");
                }
                return new HistoricalPriceRef(nextPrice, nextRow.timestamp().toLocalDate(), "SYSTEM_HISTORY", "FALLBACK");
            }
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

        // AlÄ±m tarihinden itibaren yalnÄ±zca veri olan iÅŸlem gÃ¼nleri (gÃ¼n baÅŸÄ±na tek mum).
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
        // GÃ¼nlÃ¼k mum son noktasÄ± bugÃ¼n olsa bile alÄ±ÅŸ anÄ±ndaki kapanÄ±ÅŸtan farklÄ± olabilir; canlÄ± kotasyonu yansÄ±t.
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

    private boolean needsHistoricalUsdTrySeries(AssetType type, String symbol) {
        if (type == AssetType.FUND && marketDataClient.isTryQuotedFund(symbol)) {
            return false;
        }
        return HistoricalUsdTryConversion.needsHistoricalUsdTry(type, symbol);
    }

    private BigDecimal resolveUsdTryRate() {
        BigDecimal rate = nz(marketDataClient.getPriceTry(AssetType.FX, "USDTRY"));
        if (rate.signum() <= 0) {
            // Keep backward compatibility: if FX feed is unavailable, assume already-TRY history.
            return BigDecimal.ONE;
        }
        return rate;
    }

    private List<MarketPriceHistoryDto> normalizeHistoryPricesToTry(
            List<MarketPriceHistoryDto> history,
            AssetType type,
            String symbol,
            List<MarketPriceHistoryDto> usdTryHistory,
            BigDecimal spotUsdTryFallback
    ) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (!needsHistoricalUsdTrySeries(type, symbol)) {
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

    private BigDecimal resolveUsdTryAt(
            LocalDateTime assetTs,
            List<MarketPriceHistoryDto> fxSorted,
            BigDecimal spotUsdTryFallback
    ) {
        return HistoricalUsdTryConversion.resolveUsdTryAt(assetTs, fxSorted, spotUsdTryFallback);
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
     * Güncel fiyat spot endpoint'ten geldiği için günlük candle coverage en fazla son kapanmış güne kadar zorunlu.
     * Gece yarısı civarı "bugün" mumu henüz oluşmadan gereksiz PREPARING'e düşmemek için coverage'i dünkü güne cap'leriz.
     */
    private static LocalDate cryptoHistoryCoverageTo(LocalDate historyFrom, LocalDate today) {
        LocalDate latestClosedDay = today.minusDays(1);
        if (latestClosedDay.isBefore(historyFrom)) {
            return historyFrom;
        }
        return latestClosedDay;
    }

    private BigDecimal resolveCurrentPriceTry(
            AssetType type,
            String symbol,
            List<MarketPriceHistoryDto> normalizedHistory
    ) {
        BigDecimal spot = nz(marketDataClient.getPriceTry(currentPriceLookupType(type, symbol), currentPriceLookupSymbol(type, symbol)));
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

    private List<MarketPriceHistoryDto> loadHistory(
            AssetType type,
            String symbol,
            LocalDate from,
            LocalDate to,
            int days
    ) {
        return switch (type) {
            case METAL -> loadChartCompatibleOrFallback(type, symbol, days,
                    () -> marketDataClient.getMetalHistoryBetween(symbol, from, to));
            case BIST -> marketDataClient.getBistHistoryBetween(stripBistSuffix(symbol), from, to);
            case STOCK -> {
                if (HistoricalUsdTryConversion.isBistSymbol(symbol)) {
                    String lookupSymbol = stripBistSuffix(symbol);
                    Map<String, List<MarketPriceHistoryDto>> mapped =
                            marketDataClient.getBistBatchHistoryMapped(lookupSymbol, from, to);
                    List<MarketPriceHistoryDto> exact = mapped.get(lookupSymbol);
                    yield exact != null && !exact.isEmpty()
                            ? exact
                            : mapped.values().stream().findFirst().orElse(List.of());
                }
                yield loadChartCompatibleOrFallback(type, symbol, days,
                        () -> marketDataClient.getHistory(type, symbol, days));
            }
            case FX -> loadChartCompatibleOrFallback(type, symbol, days,
                    () -> marketDataClient.getHistory(type, symbol, days));
            case CRYPTO -> marketDataClient.getCryptoPrefilledHistory(symbol, days, "daily");
            default -> marketDataClient.getHistory(type, symbol, days);
        };
    }

    private List<MarketPriceHistoryDto> loadChartCompatibleOrFallback(
            AssetType type,
            String symbol,
            int days,
            Supplier<List<MarketPriceHistoryDto>> fallbackSupplier
    ) {
        List<MarketPriceHistoryDto> chartCompatible = marketDataClient.getChartCompatibleHistory(type, symbol, days, "daily");
        if (chartCompatible != null && !chartCompatible.isEmpty()) {
            return chartCompatible;
        }
        List<MarketPriceHistoryDto> fallback = fallbackSupplier != null ? fallbackSupplier.get() : null;
        return fallback != null ? fallback : List.of();
    }

    private BigDecimal resolveSameDayHourlyPriceTry(
            AssetType type,
            String symbol,
            LocalDate buyDate,
            LocalDate today,
            List<MarketPriceHistoryDto> fxSorted,
            BigDecimal spotUsdTryFallback
    ) {
        if (!supportsHourlyExactLookup(type, symbol)) {
            return null;
        }
        long ageDays = ChronoUnit.DAYS.between(buyDate, today);
        if (ageDays < 0 || ageDays > HOURLY_EXACT_MAX_AGE_DAYS) {
            return null;
        }
        int days = AllowedHistoryDays.smallestCovering(ageDays + 1);
        List<MarketPriceHistoryDto> hourly = type == AssetType.CRYPTO
                ? marketDataClient.getCryptoPrefilledHistory(symbol, days, "hourly")
                : marketDataClient.getChartCompatibleHistory(type, symbol, days, "hourly");
        return hourly.stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .filter(h -> h.timestamp().atZone(TZ).toLocalDate().isEqual(buyDate))
                .max(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .map(h -> normalizeHistoryPricesToTry(List.of(h), type, symbol, fxSorted, spotUsdTryFallback))
                .filter(list -> !list.isEmpty())
                .map(list -> midPrice(list.getFirst()))
                .filter(px -> px != null && px.signum() > 0)
                .orElse(null);
    }

    private boolean supportsHourlyExactLookup(AssetType type, String symbol) {
        if (type == AssetType.STOCK && HistoricalUsdTryConversion.isBistSymbol(symbol)) {
            return false;
        }
        return type == AssetType.FX || type == AssetType.CRYPTO || type == AssetType.STOCK || type == AssetType.METAL;
    }

    private static AssetType currentPriceLookupType(AssetType type, String symbol) {
        if (type == AssetType.STOCK && HistoricalUsdTryConversion.isBistSymbol(symbol)) {
            return AssetType.BIST;
        }
        return type;
    }

    private static String currentPriceLookupSymbol(AssetType type, String symbol) {
        if (type == AssetType.BIST || (type == AssetType.STOCK && HistoricalUsdTryConversion.isBistSymbol(symbol))) {
            return stripBistSuffix(symbol);
        }
        return symbol;
    }

    private static String stripBistSuffix(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        return normalized.endsWith(".IS") ? normalized.substring(0, normalized.length() - 3) : normalized;
    }

    private record HistoricalPriceRef(
            BigDecimal priceTry,
            LocalDate priceDate,
            String source,
            String qualityFlag
    ) {
    }
}
