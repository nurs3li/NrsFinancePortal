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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketPriceQueryService {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(30, 90, 180, 365);
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
    // NEW: BATCH HISTORY (OHLC)
    // =========================
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
            throw new InvalidRequestException("days sadece 30, 90, 180, 365 olabilir.");
        }
    }

    private List<String> normalizeAndValidateSymbols(MarketType type, List<String> rawSymbols) {
        if (rawSymbols == null || rawSymbols.isEmpty()) {
            throw new InvalidRequestException("symbols zorunludur. En az 2 sembol gönderin.");
        }

        List<String> symbols = rawSymbols.stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();

        if (symbols.size() < 2) {
            throw new InvalidRequestException("Batch compare için en az 2 sembol gerekli.");
        }
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
}