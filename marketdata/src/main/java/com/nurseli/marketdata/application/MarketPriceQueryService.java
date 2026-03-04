package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.BatchHistoryResponse;
import com.nurseli.marketdata.api.dto.CandlePointResponse;
import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.MarketType;
import com.nurseli.marketdata.api.exception.InvalidRequestException;
import com.nurseli.marketdata.domain.price.CryptoSymbolMapping;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
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

    private static final Set<Integer> ALLOWED_DAYS = Set.of(7, 14, 30, 90, 180, 365);
    private static final int MAX_SYMBOLS = 8;
    private static final BigDecimal ZERO_VOLUME = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

    private final MarketPriceHistoryRepository repository;

    public MarketPriceLatestResponse getLatestOrThrow(String symbol) {
        return repository
                .findTopBySymbolOrderByTimestampDesc(symbol)
                .map(e -> new MarketPriceLatestResponse(
                        e.getSymbol(),
                        e.getBuyPrice(),
                        e.getSellPrice(),
                        e.getSource(),
                        e.getTimestamp()
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
                                e.getTimestamp()
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
        return getLatestBySource("TEFAS");
    }

    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        List<MarketPriceBucketView> buckets = repository.findBucketedHistory(symbol, start, end);

        return buckets.stream()
                .map(b -> new MarketPriceHistoryResponse(
                        b.getBuyPrice(),
                        b.getSellPrice(),
                        b.getTimestamp()
                ))
                .toList();
    }

    // =========================
    // NEW: BATCH HISTORY (OHLC) + CACHE
    // =========================
    @Cacheable(
            cacheNames = "market:batch",
            key = "T(String).format('%s|%s|%d', #rawType, #rawSymbols != null ? #rawSymbols.toString() : '', #days)",
            condition = "#days <= 14" // kısa aralık (7/14 gün) için cache
    )
    public BatchHistoryResponse getBatchHistory(String rawType, List<String> rawSymbols, int days) {
        MarketType type = MarketType.from(rawType);
        validateDays(days);

        List<String> symbols = normalizeAndValidateSymbols(type, rawSymbols);

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        Map<String, List<CandlePointResponse>> series = new LinkedHashMap<>();

        for (String symbol : symbols) {
            List<MarketPriceHistory> rows =
                    repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(symbol, start, end);

            series.put(symbol, toDailyCandles(rows));
        }

        return new BatchHistoryResponse(series);
    }

    private void validateDays(int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new InvalidRequestException("days sadece 7, 14, 30, 90, 180, 365 olabilir.");
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

        // 🔴 ESKİ KURAL: Batch compare için en az 2 sembol
        // if (symbols.size() < 2) {
        //     throw new InvalidRequestException("Batch compare için en az 2 sembol gerekli.");
        // }

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
            case FUNDS -> true; // fon kodları dinamik olabilir (TEFAS)
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
                    ZERO_VOLUME
            ));
        }

        return candles;
    }

    private BigDecimal mid(MarketPriceHistory row) {
        return row.getBuyPrice()
                .add(row.getSellPrice())
                .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }

    // =========================
    // NEW: INDICATORS (MA + trend) + CACHE
    // =========================
    @Cacheable(
            cacheNames = "market:indicators",
            key = "#rawType + '|' + #rawSymbol + '|' + #days + '|' + (#rawMa != null ? #rawMa : '')",
            condition = "#days <= 14" // kısa aralık istekleri için cache
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

        List<CandlePointResponse> candles = toDailyCandles(rows);
        if (candles.size() < 2) {
            throw new InvalidRequestException("Trend hesaplamak için en az 2 günlük veri gerekli.");
        }

        List<IndicatorPointResponse> closeSeries = candles.stream()
                .map(c -> new IndicatorPointResponse(c.t(), c.c()))
                .toList();

        Map<Integer, List<IndicatorPointResponse>> maSeries = buildMovingAverages(closeSeries, maWindows);
        TrendResponse trend = calculateTrend(closeSeries);

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
        double[] y = new double[n]; // base=100 normalize seri

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
        double slope = den == 0.0 ? 0.0 : num / den; // base100 puan / gün

        // R^2 (lineer uygunluk)
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

        double returnScore = Math.min(Math.abs(normalizedReturn) / 0.10, 1.0); // %10 ve üzeri max
        double strength = Math.max(0.0, Math.min(1.0, returnScore * 0.6 + r2 * 0.4));

        return new TrendResponse(
                direction,
                BigDecimal.valueOf(slope).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(normalizedReturn).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(strength).setScale(6, RoundingMode.HALF_UP)
        );
    }
}