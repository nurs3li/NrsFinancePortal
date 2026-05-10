package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.BatchHistoryResponse;
import com.nurseli.marketdata.api.dto.CandlePointResponse;
import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.MarketType;
import com.nurseli.marketdata.api.dto.DataQualityFlag;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.config.EquityProperties;
import com.nurseli.marketdata.config.EtfProperties;
import com.nurseli.marketdata.domain.price.CryptoSymbolMapping;
import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import com.nurseli.marketdata.domain.price.FxDailyCandle;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.repository.CryptoDailyCandleRepository;
import com.nurseli.marketdata.repository.EquityDailyCandleRepository;
import com.nurseli.marketdata.repository.FxDailyCandleRepository;
import com.nurseli.marketdata.repository.MarketPriceBucketView;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.nurseli.marketdata.api.dto.IndicatorPointResponse;
import com.nurseli.marketdata.api.dto.MarketIndicatorsResponse;
import com.nurseli.marketdata.api.dto.TrendResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketPriceQueryService {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(1, 7, 14, 30, 90, 180, 365);
    private static final int MAX_SYMBOLS = 8;
    private static final BigDecimal ZERO_VOLUME = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

    private final MarketPriceHistoryRepository repository;
    private final CryptoDailyCandleRepository cryptoDailyCandleRepository;
    private final EquityDailyCandleRepository equityDailyCandleRepository;
    private final FxDailyCandleRepository fxDailyCandleRepository;
    private final EquityProperties equityProperties;
    private final EtfProperties etfProperties;
    private final EquityMarketCapService equityMarketCapService;
    private final MetalPriceIngestService metalPriceIngestService;

    public MarketPriceLatestResponse getLatestOrThrow(String symbol) {
        return repository
                .findTopBySymbolOrderByTimestampDesc(symbol)
                .map(e -> new MarketPriceLatestResponse(
                        e.getSymbol(),
                        e.getBuyPrice(),
                        e.getSellPrice(),
                        e.getSource(),
                        e.getTimestamp(),
                        e.getTimestamp(),
                        PriceQuality.EXACT,
                        null,
                        null,
                        null
                ))
                .orElseThrow(() ->
                        new IllegalStateException("No data found for symbol: " + symbol)
                );
    }

    public Map<String, MarketPriceLatestResponse> getLatestBySource(String source) {
        return repository.findLatestBySource(source)
                .stream()
                .collect(Collectors.toMap(
                        MarketPriceHistory::getSymbol,
                        e -> new MarketPriceLatestResponse(
                                e.getSymbol(),
                                e.getBuyPrice(),
                                e.getSellPrice(),
                                e.getSource(),
                                e.getTimestamp(),
                                e.getTimestamp(),
                                PriceQuality.EXACT,
                                null,
                                null,
                                null
                        ),
                        (a, b) -> a.timestamp().isAfter(b.timestamp()) ? a : b,
                        LinkedHashMap::new
                ));
    }

    public Map<String, MarketPriceLatestResponse> getLatestCrypto() {
        return getLatestBySource("COINGECKO");
    }

    public Map<String, MarketPriceLatestResponse> getLatestFx() {
        return getLatestBySource("TCMB");
    }

    public Map<String, MarketPriceLatestResponse> getLatestMetals() {
        return getLatestBySource("COINGECKO");
    }

    public Map<String, MarketPriceLatestResponse> getLatestFunds() {
        return getLatestBySource("ETF");
    }

    public Map<String, MarketPriceLatestResponse> getLatestEquity() {
        Map<String, MarketPriceLatestResponse> latest = getLatestBySource("FINHUB");
        Map<String, MarketPriceLatestResponse> out = new LinkedHashMap<>();
        for (var e : latest.entrySet()) {
            String symbol = e.getKey();
            MarketPriceLatestResponse row = e.getValue();
            EquityMarketCapInfo marketCapInfo = equityMarketCapService.getMarketCap(symbol);
            out.put(symbol, new MarketPriceLatestResponse(
                    row.symbol(),
                    row.buyPrice(),
                    row.sellPrice(),
                    row.source(),
                    row.timestamp(),
                    row.asOf(),
                    row.quality(),
                    marketCapInfo != null ? marketCapInfo.marketCapUsd() : null,
                    marketCapInfo != null ? marketCapInfo.marketCapSource() : null,
                    marketCapInfo != null ? marketCapInfo.marketCapAsOf() : null
            ));
        }
        return out;
    }

    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        String sym = symbol.trim().toUpperCase();
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        List<MarketPriceBucketView> buckets = repository.findBucketedHistory(sym, start, end);

        return buckets.stream()
                .map(b -> new MarketPriceHistoryResponse(
                        b.getBuyPrice(),
                        b.getSellPrice(),
                        b.getTimestamp(),
                        "SYSTEM",
                        b.getTimestamp(),
                        DataQualityFlag.EXACT
                ))
                .toList();
    }

    /**
     * Hisse geçmişi — yalnızca yapılandırılmış equity sembolleri (app.equity.symbols).
     */
    public List<MarketPriceHistoryResponse> getEquityHistory(String rawSymbol, int days) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(MarketType.EQUITY, symbol)) {
            throw new InvalidRequestException("type=EQUITY için geçersiz symbol: " + symbol);
        }
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        List<CandlePointResponse> candles = toEquityCandles(symbol, start.toLocalDate(), end.toLocalDate());
        if (!candles.isEmpty()) {
            return toHistoryFromCandles(candles);
        }
        return getHistory(symbol, days);
    }

    public List<MarketPriceHistoryResponse> getCryptoHistory(String rawSymbol, int days) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(MarketType.CRYPTO, symbol)) {
            String maybeUsdt = symbol.endsWith("USDT") ? symbol : symbol + "USDT";
            if (isAllowedSymbol(MarketType.CRYPTO, maybeUsdt)) {
                symbol = maybeUsdt;
            } else {
                throw new InvalidRequestException("type=CRYPTO için geçersiz symbol: " + symbol);
            }
        }
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        List<CandlePointResponse> candles = toCryptoCandles(symbol, start.toLocalDate(), end.toLocalDate());
        if (!candles.isEmpty()) {
            return toHistoryFromCandles(candles);
        }
        return getHistory(symbol, days);
    }

    /**
     * Altın (XAU_TRY) geçmişi — piyasa batch grafiğiyle aynı kaynak: ham satırlar → günlük mum.
     * Dakika kovası ({@link #getHistory}) çok büyük seri üretebildiği için simülasyon / uzun aralıkta
     * tüketici istemcilerde zaman aşımı veya boş yanıt riski vardı.
     */
    public List<MarketPriceHistoryResponse> getMetalHistory(String rawSymbol, int days) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(MarketType.METALS, symbol)) {
            throw new InvalidRequestException("type=METALS için geçersiz symbol: " + symbol);
        }
        if (days <= 0) {
            return List.of();
        }
        metalPriceIngestService.ensureHistoricalBackfill(days);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        List<MarketPriceHistory> rows =
                repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);
        List<CandlePointResponse> candles = toDailyCandles(rows);
        if (!candles.isEmpty()) {
            return toHistoryFromCandles(candles);
        }
        return getHistory(symbol, days);
    }

    /**
     * Döviz (USDTRY/EURTRY/GBPTRY) geçmişi — günlük mum tablosundan, gün sayısı için batch kısıtı yok.
     * Uzun aralıklı simülasyon isteklerinde {@link #getBatchHistory} çağrısı {@link #validateDays} nedeniyle
     * başarısız olabildiğinden history uç noktasında bu yol kullanılır.
     */
    public List<MarketPriceHistoryResponse> getFxHistory(String rawSymbol, int days) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(MarketType.FX, symbol)) {
            throw new InvalidRequestException("type=FX için geçersiz symbol: " + symbol);
        }
        if (days <= 0) {
            return List.of();
        }
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        List<CandlePointResponse> candles = toFxCandles(symbol, start.toLocalDate(), end.toLocalDate());
        if (!candles.isEmpty()) {
            return toHistoryFromCandles(candles);
        }
        return getHistory(symbol, days);
    }

    // =========================
    // BATCH HISTORY (OHLC) + CACHE
    // =========================
    @Cacheable(
            cacheNames = "market:batch",
            key = "T(String).format('%s|%s|%d', #rawType, #rawSymbols != null ? #rawSymbols.toString() : '', #days)",
            condition = "#days <= 14"
    )
    public BatchHistoryResponse getBatchHistory(String rawType, List<String> rawSymbols, int days) {
        MarketType type = MarketType.from(rawType);
        validateDays(days);

        List<String> symbols = normalizeAndValidateSymbols(type, rawSymbols);

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        Map<String, List<CandlePointResponse>> series = new LinkedHashMap<>();

        for (String symbol : symbols) {
            if (type == MarketType.FX) {
                List<CandlePointResponse> fxCandles = toFxCandles(symbol, start.toLocalDate(), end.toLocalDate());
                if (!fxCandles.isEmpty()) {
                    series.put(symbol, fxCandles);
                    continue;
                }
            }
            if (type == MarketType.CRYPTO) {
                List<CandlePointResponse> cryptoCandles = toCryptoCandles(symbol, start.toLocalDate(), end.toLocalDate());
                if (!cryptoCandles.isEmpty()) {
                    series.put(symbol, cryptoCandles);
                    continue;
                }
            }
            if (type == MarketType.EQUITY) {
                List<CandlePointResponse> equityCandles = toEquityCandles(symbol, start.toLocalDate(), end.toLocalDate());
                if (!equityCandles.isEmpty()) {
                    series.put(symbol, equityCandles);
                    continue;
                }
            }
            List<MarketPriceHistory> rows = repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);
            series.put(symbol, toDailyCandles(rows));
        }

        return new BatchHistoryResponse(series);
    }

    private void validateDays(int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new InvalidRequestException("days sadece 1, 7, 14, 30, 90, 180, 365 olabilir.");
        }
    }

    private List<String> normalizeAndValidateSymbols(MarketType type, List<String> rawSymbols) {
        if (rawSymbols == null || rawSymbols.isEmpty()) {
            throw new InvalidRequestException("symbols zorunludur. En az 1 sembol gönderin.");
        }

        List<String> symbols = rawSymbols.stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();

        if (symbols.size() > MAX_SYMBOLS) {
            throw new InvalidRequestException("En fazla " + MAX_SYMBOLS + " sembol gönderebilirsiniz.");
        }

        List<String> invalid = symbols.stream()
                .filter(s -> !isAllowedSymbol(type, s))
                .toList();

        if (!invalid.isEmpty()) {
            throw new InvalidRequestException("type=" + type + " için geçersiz symbol(ler): " + invalid);
        }

        return symbols;
    }

    private boolean isAllowedSymbol(MarketType type, String symbol) {
        return switch (type) {
            case FX -> Set.of("USDTRY", "EURTRY", "GBPTRY").contains(symbol);
            case CRYPTO -> CryptoSymbolMapping.SYMBOL_TO_ID.containsKey(symbol);
            case METALS -> Set.of("XAU_TRY").contains(symbol);
            case FUNDS -> etfProperties != null
                    && etfProperties.getSymbols() != null
                    && etfProperties.getSymbols().stream()
                    .map(String::toUpperCase)
                    .anyMatch(s -> s.equals(symbol));
            case EQUITY -> equityProperties != null
                    && equityProperties.getSymbols() != null
                    && equityProperties.getSymbols().stream()
                    .map(String::toUpperCase)
                    .anyMatch(s -> s.equals(symbol));
        };
    }

    private List<CandlePointResponse> toDailyCandles(List<MarketPriceHistory> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        Map<LocalDate, List<MarketPriceHistory>> byDay = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getTimestamp().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<CandlePointResponse> candles = new ArrayList<>();

        for (Map.Entry<LocalDate, List<MarketPriceHistory>> entry : byDay.entrySet()) {
            List<MarketPriceHistory> dayRows = entry.getValue();
            dayRows.sort(Comparator.comparing(MarketPriceHistory::getTimestamp));

            BigDecimal open = mid(dayRows.get(0));
            BigDecimal close = mid(dayRows.get(dayRows.size() - 1));

            BigDecimal high = dayRows.stream()
                    .map(this::mid)
                    .max(Comparator.naturalOrder())
                    .orElse(open);

            BigDecimal low = dayRows.stream()
                    .map(this::mid)
                    .min(Comparator.naturalOrder())
                    .orElse(open);

            candles.add(new CandlePointResponse(
                    entry.getKey().atStartOfDay(),
                    open,
                    high,
                    low,
                    close,
                    deriveSyntheticVolume(dayRows, open, high, low, close)
            ));
        }

        return candles;
    }

    private List<MarketPriceHistoryResponse> toHistoryFromCandles(List<CandlePointResponse> candles) {
        return candles.stream()
                .map(c -> new MarketPriceHistoryResponse(
                        c.c(),
                        c.c(),
                        c.t(),
                        "SYSTEM",
                        c.t(),
                        DataQualityFlag.EXACT
                ))
                .toList();
    }

    private List<CandlePointResponse> toEquityCandles(String symbol, LocalDate from, LocalDate to) {
        List<EquityDailyCandle> rows = equityDailyCandleRepository.findBySymbolAndAsOfBetweenOrderByAsOfAsc(symbol, from, to);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new CandlePointResponse(
                        row.getAsOf().atStartOfDay(),
                        row.getOpenPrice(),
                        row.getHighPrice(),
                        row.getLowPrice(),
                        row.getClosePrice(),
                        row.getVolume()
                ))
                .toList();
    }

    private List<CandlePointResponse> toCryptoCandles(String symbol, LocalDate from, LocalDate to) {
        List<CryptoDailyCandle> rows = cryptoDailyCandleRepository.findBySymbolAndAsOfBetweenOrderByAsOfAsc(symbol, from, to);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new CandlePointResponse(
                        row.getAsOf().atStartOfDay(),
                        row.getOpenPrice(),
                        row.getHighPrice(),
                        row.getLowPrice(),
                        row.getClosePrice(),
                        row.getVolume()
                ))
                .toList();
    }

    private List<CandlePointResponse> toFxCandles(String symbol, LocalDate from, LocalDate to) {
        List<FxDailyCandle> rows = fxDailyCandleRepository.findBySymbolAndAsOfBetweenOrderByAsOfAsc(symbol, from, to);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new CandlePointResponse(
                        row.getAsOf().atStartOfDay(),
                        row.getOpenPrice(),
                        row.getHighPrice(),
                        row.getLowPrice(),
                        row.getClosePrice(),
                        row.getVolume()
                ))
                .toList();
    }

    private BigDecimal mid(MarketPriceHistory row) {
        return row.getBuyPrice()
                .add(row.getSellPrice())
                .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal deriveSyntheticVolume(
            List<MarketPriceHistory> dayRows,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close
    ) {
        if (dayRows == null || dayRows.isEmpty()) {
            return ZERO_VOLUME;
        }
        BigDecimal maxPrice = List.of(open, high, low, close).stream()
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal minPrice = List.of(open, high, low, close).stream()
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal range = maxPrice.subtract(minPrice).abs();
        if (maxPrice.signum() <= 0) {
            return BigDecimal.valueOf(dayRows.size());
        }
        BigDecimal volatilityScore = range.divide(maxPrice, 8, RoundingMode.HALF_UP);
        BigDecimal tradeCountScore = BigDecimal.valueOf(dayRows.size());
        return tradeCountScore
                .multiply(BigDecimal.valueOf(1000))
                .multiply(BigDecimal.ONE.add(volatilityScore))
                .setScale(6, RoundingMode.HALF_UP);
    }

    // =========================
    // INDICATORS (MA + trend) + CACHE
    // =========================
    @Cacheable(
            cacheNames = "market:indicators",
            key = "#rawType + '|' + #rawSymbol + '|' + #days + '|' + (#rawMa != null ? #rawMa : '')",
            condition = "#days <= 14"
    )
    public MarketIndicatorsResponse getIndicators(String rawType, String rawSymbol, int days, String rawMa) {
        MarketType type = MarketType.from(rawType);
        validateDays(days);

        String symbol = normalizeAndValidateSingleSymbol(type, rawSymbol);
        List<Integer> maWindows = parseMaWindows(rawMa, days);

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        List<MarketPriceHistory> rows =
                repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);

        List<CandlePointResponse> candles = switch (type) {
            case EQUITY -> toEquityCandles(symbol, start.toLocalDate(), end.toLocalDate());
            case CRYPTO -> toCryptoCandles(symbol, start.toLocalDate(), end.toLocalDate());
            case FX -> toFxCandles(symbol, start.toLocalDate(), end.toLocalDate());
            default -> List.of();
        };
        if (candles.isEmpty()) {
            candles = toDailyCandles(rows);
        }
        if (candles.isEmpty()) {
            throw new InvalidRequestException("Gosterge hesaplamak icin en az 1 gunluk veri gerekli.");
        }

        List<IndicatorPointResponse> closeSeries = candles.stream()
                .map(c -> new IndicatorPointResponse(c.t(), c.c()))
                .toList();

        Map<Integer, List<IndicatorPointResponse>> maSeries = buildMovingAverages(closeSeries, maWindows);
        TrendResponse trend = closeSeries.size() < 2
                ? new TrendResponse(
                        "FLAT",
                        BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP)
                )
                : calculateTrend(closeSeries);

        return new MarketIndicatorsResponse(type.name(), symbol, days, closeSeries, maSeries, trend);
    }

    private String normalizeAndValidateSingleSymbol(MarketType type, String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new InvalidRequestException("symbol zorunludur.");
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (!isAllowedSymbol(type, symbol)) {
            throw new InvalidRequestException("type=" + type + " için geçersiz symbol: " + symbol);
        }
        return symbol;
    }

    private List<Integer> parseMaWindows(String rawMa, int days) {
        if (days < 2) {
            return List.of();
        }
        String value = (rawMa == null || rawMa.isBlank()) ? "7,30,90" : rawMa;

        List<Integer> windows = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException ex) {
                        throw new InvalidRequestException("ma parametresi sayı olmalı. Örnek: 7,30,90");
                    }
                })
                .distinct()
                .sorted()
                .toList();

        if (windows.isEmpty()) {
            throw new InvalidRequestException("En az bir MA değeri gönderilmelidir.");
        }

        for (Integer w : windows) {
            if (w < 2) {
                throw new InvalidRequestException("MA değeri 2 veya daha büyük olmalı.");
            }
            if (w > days) {
                throw new InvalidRequestException("MA değeri days parametresinden büyük olamaz. MA=" + w + ", days=" + days);
            }
        }

        return windows;
    }

    private Map<Integer, List<IndicatorPointResponse>> buildMovingAverages(
            List<IndicatorPointResponse> closeSeries,
            List<Integer> windows
    ) {
        Map<Integer, List<IndicatorPointResponse>> result = new LinkedHashMap<>();

        for (Integer window : windows) {
            List<IndicatorPointResponse> points = new ArrayList<>();

            for (int i = window - 1; i < closeSeries.size(); i++) {
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = i - window + 1; j <= i; j++) {
                    sum = sum.add(closeSeries.get(j).value());
                }
                BigDecimal avg = sum.divide(BigDecimal.valueOf(window), 6, RoundingMode.HALF_UP);
                points.add(new IndicatorPointResponse(closeSeries.get(i).t(), avg));
            }

            result.put(window, points);
        }

        return result;
    }

    private TrendResponse calculateTrend(List<IndicatorPointResponse> closeSeries) {
        int n = closeSeries.size();

        double first = closeSeries.get(0).value().doubleValue();
        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            double close = closeSeries.get(i).value().doubleValue();
            y[i] = (close / first) * 100.0;
        }

        double xMean = (n - 1) / 2.0;
        double yMean = Arrays.stream(y).average().orElse(100.0);

        double num = 0.0;
        double den = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = i - xMean;
            num += dx * (y[i] - yMean);
            den += dx * dx;
        }
        double slope = den == 0.0 ? 0.0 : num / den;

        double ssTot = 0.0;
        double ssRes = 0.0;
        double intercept = yMean - slope * xMean;
        for (int i = 0; i < n; i++) {
            double pred = intercept + slope * i;
            ssTot += Math.pow(y[i] - yMean, 2);
            ssRes += Math.pow(y[i] - pred, 2);
        }
        double r2 = ssTot == 0.0 ? 0.0 : Math.max(0.0, 1.0 - (ssRes / ssTot));

        double last = closeSeries.get(n - 1).value().doubleValue();
        double normalizedReturn = (last / first) - 1.0;

        String direction;
        if (slope > 0.03) {
            direction = "UP";
        } else if (slope < -0.03) {
            direction = "DOWN";
        } else {
            direction = "FLAT";
        }

        double returnScore = Math.min(Math.abs(normalizedReturn) / 0.10, 1.0);
        double strength = Math.max(0.0, Math.min(1.0, returnScore * 0.6 + r2 * 0.4));

        return new TrendResponse(
                direction,
                BigDecimal.valueOf(slope).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(normalizedReturn).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(strength).setScale(6, RoundingMode.HALF_UP)
        );
    }
}