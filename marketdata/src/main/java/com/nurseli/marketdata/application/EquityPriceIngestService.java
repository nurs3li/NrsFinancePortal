package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.EquityProperties;
import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import com.nurseli.marketdata.infrastructure.finhub.FinHubCandleDto;
import com.nurseli.marketdata.infrastructure.finhub.FinHubQuoteDto;
import com.nurseli.marketdata.infrastructure.stooq.StooqCsvClient;
import com.nurseli.marketdata.infrastructure.yahoo.YahooChartClient;
import com.nurseli.marketdata.repository.EquityDailyCandleRepository;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class EquityPriceIngestService {

    /** Finnhub {@code t} anlık UTC epoch; DB {@code timestamp without time zone} ile İstanbul duvar saati tutarlı olsun. */
    private static final ZoneId MARKET_WALL_CLOCK_ZONE = ZoneId.of("Europe/Istanbul");

    /** Anlık Finnhub quote satırlarından türetilen günlük mum (harici mum API'si olmadan). */
    public static final String SOURCE_FINHUB_QUOTE_ROLLUP = "FINHUB_QUOTE_ROLLUP";

    private final FinHubClient finHubClient;
    private final StooqCsvClient stooqCsvClient;
    private final YahooChartClient yahooChartClient;
    private final MarketPriceHistoryRepository repository;
    private final EquityDailyCandleRepository equityDailyCandleRepository;
    private final EquityProperties equityProperties;

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveEquityQuotes() {
        List<String> symbols = equityProperties.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            log.debug("[EQUITY] No symbols configured, skipping");
            return;
        }

        for (String symbol : symbols) {
            try {
                FinHubQuoteDto quote = finHubClient.fetchQuote(symbol).block();
                if (quote == null || quote.getC() == null) {
                    log.warn("[EQUITY] No quote for symbol={}", symbol);
                    continue;
                }

                BigDecimal mid = BigDecimal.valueOf(quote.getC());
                MarketPriceHistory entity = new MarketPriceHistory();
                entity.setSymbol(symbol);
                entity.setBuyPrice(SpreadCalculator.buyPrice(mid));
                entity.setSellPrice(SpreadCalculator.sellPrice(mid));
                entity.setSource("FINHUB");
                LocalDateTime timestamp = quote.getT() != null
                        ? LocalDateTime.ofInstant(Instant.ofEpochSecond(quote.getT()), MARKET_WALL_CLOCK_ZONE)
                        : LocalDateTime.now(MARKET_WALL_CLOCK_ZONE);
                entity.setTimestamp(timestamp);

                repository.save(entity);
                log.info("[EQUITY] Saved {} = {}", symbol, mid);
            } catch (Exception e) {
                log.error("[EQUITY] Failed for symbol={}: {}", symbol, e.getMessage());
            }
        }
        LocalDate endDay = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        LocalDate startRollup = endDay.minusDays(2);
        for (String symbol : symbols) {
            try {
                rollupEquityDailyCandlesFromFinhubQuotes(symbol, startRollup, endDay);
            } catch (Exception ex) {
                log.warn("[EQUITY_ROLLUP] symbol={} reason={}", symbol, ex.getMessage());
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveOneYearHistoryBackfill() {
        int days = Math.max(1, equityProperties.getMaxHistoryDays());
        fetchAndSaveHistoryBackfill(days, 20);
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveHistoryBackfill(int periodDays) {
        fetchAndSaveHistoryBackfill(periodDays, 20);
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveHistoryBackfill(int periodDays, int batchSize) {
        List<String> symbols = equityProperties.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            log.debug("[EQUITY_HISTORY] No symbols configured for backfill");
            return;
        }
        LocalDate to = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        LocalDate from = to.minusDays(Math.max(periodDays, 1));
        int size = Math.max(1, batchSize);
        for (int i = 0; i < symbols.size(); i += size) {
            List<String> batch = symbols.subList(i, Math.min(symbols.size(), i + size));
            for (String symbol : batch) {
                try {
                    ingestHistoryForSymbol(symbol, from, to, true);
                    rollupEquityDailyCandlesFromFinhubQuotes(symbol, from, to);
                } catch (Exception ex) {
                    log.warn("[EQUITY_HISTORY] Backfill failed symbol={} reason={}", symbol, ex.getMessage());
                }
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveIncrementalDailyHistory() {
        List<String> symbols = equityProperties.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            log.debug("[EQUITY_HISTORY] No symbols configured for incremental ingest");
            return;
        }
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        for (String symbol : symbols) {
            try {
                // Son satır market_price_history içinde çoğunlukla 10 dk'da bir gelen FINHUB quote'tur;
                // grafiğin kaynağı equity_daily_candle olduğu için "son gün" buradan türetilmeli (aksi halde
                // from = bugün+1 olur ve incremental hiç çalışmaz).
                int maxDays = Math.max(1, equityProperties.getMaxHistoryDays());
                LocalDate fromRaw = equityDailyCandleRepository.findTopBySymbolOrderByAsOfDesc(symbol)
                        .map(c -> c.getAsOf().plusDays(1))
                        .orElse(today.minusDays(maxDays));
                int heal = Math.max(0, equityProperties.getIncrementalGapHealDays());
                LocalDate from = fromRaw.minusDays(heal);
                LocalDate oldest = today.minusDays(maxDays);
                if (from.isBefore(oldest)) {
                    from = oldest;
                }
                if (from.isAfter(today)) {
                    continue;
                }
                ingestHistoryForSymbol(symbol, from, today, true);
                rollupEquityDailyCandlesFromFinhubQuotes(symbol, from, today);
            } catch (Exception ex) {
                log.warn("[EQUITY_HISTORY] Incremental failed symbol={} reason={}", symbol, ex.getMessage());
            }
        }
    }

    /**
     * DB'deki anlık {@code FINHUB} quote satırlarını takvim gününe göre gruplayıp OHLC üretir ve
     * {@code equity_daily_candle} içine yazar / günceller. Böylece Finnhub mum kotası olmadan da
     * grafik son günleri sizin gelen veriden güncellenir; geçmiş Yahoo/Stooq mumlarının üzerine
     * (aynı günde quote varsa) bu kaynak önceliklidir.
     */
    public void rollupEquityDailyCandlesFromFinhubQuotes(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null || symbol.isBlank() || from == null || to == null || from.isAfter(to)) {
            return;
        }
        String sym = symbol.trim().toUpperCase();
        LocalDateTime start = from.atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
        LocalDateTime end = to.plusDays(1).atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
        List<MarketPriceHistory> rows =
                repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(sym, start, end);
        Map<LocalDate, List<MarketPriceHistory>> byDay = new TreeMap<>();
        for (MarketPriceHistory r : rows) {
            if (!"FINHUB".equals(r.getSource())) {
                continue;
            }
            LocalDate d = r.getTimestamp().toLocalDate();
            if (d.isBefore(from) || d.isAfter(to)) {
                continue;
            }
            byDay.computeIfAbsent(d, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<LocalDate, List<MarketPriceHistory>> e : byDay.entrySet()) {
            List<MarketPriceHistory> dayRows = e.getValue();
            dayRows.sort(Comparator.comparing(MarketPriceHistory::getTimestamp));
            BigDecimal open = mid(dayRows.get(0));
            BigDecimal close = mid(dayRows.get(dayRows.size() - 1));
            BigDecimal high = dayRows.stream().map(this::mid).max(Comparator.naturalOrder()).orElse(open);
            BigDecimal low = dayRows.stream().map(this::mid).min(Comparator.naturalOrder()).orElse(open);
            BigDecimal vol = syntheticVolumeFromTicks(dayRows, open, high, low, close);

            EquityDailyCandle candle = equityDailyCandleRepository
                    .findBySymbolAndAsOf(sym, e.getKey())
                    .orElseGet(() -> {
                        EquityDailyCandle c = new EquityDailyCandle();
                        c.setSymbol(sym);
                        c.setAsOf(e.getKey());
                        return c;
                    });
            candle.setOpenPrice(open);
            candle.setHighPrice(high);
            candle.setLowPrice(low);
            candle.setClosePrice(close);
            candle.setVolume(vol);
            candle.setSource(SOURCE_FINHUB_QUOTE_ROLLUP);
            equityDailyCandleRepository.save(candle);
        }
        if (!byDay.isEmpty()) {
            log.info("[EQUITY_ROLLUP] symbol={} days={} window={}..{}", sym, byDay.size(), from, to);
        }
    }

    private BigDecimal mid(MarketPriceHistory row) {
        return row.getBuyPrice()
                .add(row.getSellPrice())
                .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal syntheticVolumeFromTicks(
            List<MarketPriceHistory> dayRows,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close
    ) {
        if (dayRows == null || dayRows.isEmpty()) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
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
            return BigDecimal.valueOf(dayRows.size()).setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal volatilityScore = range.divide(maxPrice, 8, RoundingMode.HALF_UP);
        BigDecimal tradeCountScore = BigDecimal.valueOf(dayRows.size());
        return tradeCountScore
                .multiply(BigDecimal.valueOf(1000))
                .multiply(BigDecimal.ONE.add(volatilityScore))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private void ingestHistoryForSymbol(String symbol, LocalDate from, LocalDate to, boolean allowStooqFallback) {
        List<DailyBar> bars = fetchDailyBarsFromFinnhub(symbol, from, to);
        String source = "FINHUB_HISTORY";
        if (bars.isEmpty() && allowStooqFallback) {
            bars = fetchDailyBarsFromStooq(symbol, from, to);
            source = "STOOQ_FALLBACK";
        }
        if (bars.isEmpty()) {
            bars = fetchDailyBarsFromYahoo(symbol, from, to);
            source = "YAHOO_FALLBACK";
        }
        if (bars.isEmpty()) {
            log.info("[EQUITY_HISTORY] No bars for symbol={} from={} to={}", symbol, from, to);
            return;
        }
        int inserted = 0;
        for (DailyBar bar : bars) {
            LocalDateTime timestamp = bar.day().atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
            if (repository.existsBySymbolAndTimestamp(symbol, timestamp)) {
                saveDailyCandleIfAbsent(symbol, bar, source);
                continue;
            }
            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(symbol);
            entity.setBuyPrice(SpreadCalculator.buyPrice(bar.closePrice()));
            entity.setSellPrice(SpreadCalculator.sellPrice(bar.closePrice()));
            entity.setSource(source);
            entity.setTimestamp(timestamp);
            repository.save(entity);
            saveDailyCandleIfAbsent(symbol, bar, source);
            inserted++;
        }
        log.info("[EQUITY_HISTORY] symbol={} source={} bars={} inserted={}", symbol, source, bars.size(), inserted);
    }

    private List<DailyBar> fetchDailyBarsFromFinnhub(String symbol, LocalDate from, LocalDate to) {
        long fromEpoch = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long toEpoch = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1;
        FinHubCandleDto dto = finHubClient.fetchDailyCandles(symbol, fromEpoch, toEpoch).block();
        if (dto == null || dto.getT() == null || dto.getC() == null || dto.getT().isEmpty() || dto.getC().isEmpty()) {
            return List.of();
        }
        if (!"ok".equalsIgnoreCase(dto.getS())) {
            return List.of();
        }
        int size = List.of(dto.getT(), dto.getC(), dto.getO(), dto.getH(), dto.getL()).stream()
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .min()
                .orElse(0);
        List<DailyBar> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Long epoch = dto.getT().get(i);
            Double close = dto.getC().get(i);
            if (epoch == null || close == null || close <= 0) {
                continue;
            }
            LocalDate day = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate();
            BigDecimal open = safePositiveDecimal(dto.getO(), i, close);
            BigDecimal high = safePositiveDecimal(dto.getH(), i, close);
            BigDecimal low = safePositiveDecimal(dto.getL(), i, close);
            BigDecimal closePrice = BigDecimal.valueOf(close);
            BigDecimal volume = safeDecimal(dto.getV(), i);
            out.add(new DailyBar(day, open, high, low, closePrice, volume));
        }
        return out;
    }

    private List<DailyBar> fetchDailyBarsFromStooq(String symbol, LocalDate from, LocalDate to) {
        return stooqCsvClient.fetchDailyRows(symbol, from, to).stream()
                .filter(r -> r.close() != null && r.close().signum() > 0)
                .map(r -> new DailyBar(
                        r.day(),
                        r.open() != null && r.open().signum() > 0 ? r.open() : r.close(),
                        r.high() != null && r.high().signum() > 0 ? r.high() : r.close(),
                        r.low() != null && r.low().signum() > 0 ? r.low() : r.close(),
                        r.close(),
                        parseVolume(r.volume())
                ))
                .toList();
    }

    private List<DailyBar> fetchDailyBarsFromYahoo(String symbol, LocalDate from, LocalDate to) {
        long spanDays = ChronoUnit.DAYS.between(from, to) + 7;
        String range = yahooChartRangeForSpan(spanDays);
        return yahooChartClient.fetchDailyBars(symbol, range).stream()
                .filter(r -> !r.day().isBefore(from) && !r.day().isAfter(to))
                .map(r -> new DailyBar(
                        r.day(),
                        r.open() != null && r.open().signum() > 0 ? r.open() : r.close(),
                        r.high() != null && r.high().signum() > 0 ? r.high() : r.close(),
                        r.low() != null && r.low().signum() > 0 ? r.low() : r.close(),
                        r.close(),
                        r.volume()
                ))
                .toList();
    }

    /** Yahoo chart `range` — sabit 1y yerine pencere; uzun backfill'de eksik mum önlenir. */
    static String yahooChartRangeForSpan(long spanDays) {
        if (spanDays <= 7) {
            return "1mo";
        }
        if (spanDays <= 35) {
            return "3mo";
        }
        if (spanDays <= 100) {
            return "6mo";
        }
        if (spanDays <= 400) {
            return "1y";
        }
        if (spanDays <= 800) {
            return "2y";
        }
        return "max";
    }

    private void saveDailyCandleIfAbsent(String symbol, DailyBar bar, String source) {
        if (equityDailyCandleRepository.existsBySymbolAndAsOf(symbol, bar.day())) {
            return;
        }
        EquityDailyCandle candle = new EquityDailyCandle();
        candle.setSymbol(symbol);
        candle.setAsOf(bar.day());
        candle.setOpenPrice(bar.openPrice());
        candle.setHighPrice(bar.highPrice());
        candle.setLowPrice(bar.lowPrice());
        candle.setClosePrice(bar.closePrice());
        candle.setVolume(bar.volume());
        candle.setSource(source);
        equityDailyCandleRepository.save(candle);
    }

    private BigDecimal safePositiveDecimal(List<Double> values, int index, Double fallback) {
        BigDecimal parsed = safeDecimal(values, index);
        if (parsed == null || parsed.signum() <= 0) {
            return BigDecimal.valueOf(fallback);
        }
        return parsed;
    }

    private BigDecimal safeDecimal(List<Double> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        Double value = values.get(index);
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value);
    }

    private BigDecimal parseVolume(String rawVolume) {
        if (rawVolume == null || rawVolume.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(rawVolume.trim().replace(",", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private record DailyBar(
            LocalDate day,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal volume
    ) {}
}