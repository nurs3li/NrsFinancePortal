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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class EquityPriceIngestService {

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
                        ? LocalDateTime.ofInstant(Instant.ofEpochSecond(quote.getT()), ZoneId.systemDefault())
                        : LocalDateTime.now();
                entity.setTimestamp(timestamp);

                repository.save(entity);
                log.info("[EQUITY] Saved {} = {}", symbol, mid);
            } catch (Exception e) {
                log.error("[EQUITY] Failed for symbol={}: {}", symbol, e.getMessage());
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveOneYearHistoryBackfill() {
        fetchAndSaveHistoryBackfill(365, 20);
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
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(periodDays, 1));
        int size = Math.max(1, batchSize);
        for (int i = 0; i < symbols.size(); i += size) {
            List<String> batch = symbols.subList(i, Math.min(symbols.size(), i + size));
            for (String symbol : batch) {
                try {
                    ingestHistoryForSymbol(symbol, from, to, true);
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
        LocalDate today = LocalDate.now();
        for (String symbol : symbols) {
            try {
                LocalDate from = repository.findTopBySymbolOrderByTimestampDesc(symbol)
                        .map(x -> x.getTimestamp().toLocalDate().plusDays(1))
                        .orElse(today.minusDays(365));
                if (from.isAfter(today)) {
                    continue;
                }
                ingestHistoryForSymbol(symbol, from, today, true);
            } catch (Exception ex) {
                log.warn("[EQUITY_HISTORY] Incremental failed symbol={} reason={}", symbol, ex.getMessage());
            }
        }
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
            LocalDateTime timestamp = bar.day().atStartOfDay();
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
        long fromEpoch = from.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long toEpoch = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() - 1;
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
            LocalDate day = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
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
        return yahooChartClient.fetchDailyBars(symbol).stream()
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