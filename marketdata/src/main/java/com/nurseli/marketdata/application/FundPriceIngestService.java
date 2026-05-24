package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.EtfProperties;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import com.nurseli.marketdata.infrastructure.finhub.FinHubCandleDto;
import com.nurseli.marketdata.infrastructure.finhub.FinHubQuoteDto;
import com.nurseli.marketdata.infrastructure.yahoo.YahooChartClient;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundPriceIngestService {

    private static final String SOURCE_ETF = "ETF";
    private static final String SOURCE_ETF_YAHOO = "ETF_YAHOO";

    private final FinHubClient finHubClient;
    private final YahooChartClient yahooChartClient;
    private final MarketPriceHistoryRepository repository;
    private final EtfProperties etfProperties;

    /** Finnhub candle API bu oturumda veri döndürmedi — tekrar denemeyi ve WARN spam'ini keser. */
    private final Set<String> finnhubHistoryNoDataSymbols = ConcurrentHashMap.newKeySet();

    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void ingestForDate(String symbol, LocalDate date) {
        try {
            FinHubQuoteDto quote = finHubClient.fetchQuote(symbol).block();
            if (quote == null || quote.getC() == null) {
                log.warn("[ETF] No quote for symbol={} referenceDate={}", symbol, date);
                return;
            }

            // Bugün için current (c), dün için previous close (pc)
            double priceValue = date.equals(LocalDate.now()) ? quote.getC() : (quote.getPc() != null ? quote.getPc() : quote.getC());
            BigDecimal mid = BigDecimal.valueOf(priceValue);

            boolean exists = repository
                    .findTopBySymbolOrderByTimestampDesc(symbol)
                    .map(e -> e.getTimestamp().toLocalDate().equals(date))
                    .orElse(false);

            if (exists) {
                log.info("[ETF] Already exists symbol={} date={}", symbol, date);
                return;
            }

            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(symbol);
            entity.setBuyPrice(SpreadCalculator.buyPrice(mid));
            entity.setSellPrice(SpreadCalculator.sellPrice(mid));
            entity.setSource(SOURCE_ETF);
            entity.setTimestamp(LocalDateTime.of(date, java.time.LocalTime.NOON));

            repository.save(entity);
            log.info("[ETF] SAVED symbol={} price={} date={}", symbol, mid, date);

        } catch (Exception e) {
            log.error("[ETF] INGEST FAILED symbol={} date={}", symbol, date, e);
        }
    }

    /**
     * DB güncelse Finnhub/Yahoo çağrısı yapmaz (restart'ta gereksiz WARN ve API yükünü önler).
     */
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void ingestHistoryIfNeeded(String symbol, int periodDays) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        LocalDate end = LocalDate.now();
        int freshDays = Math.max(1, etfProperties.getHistorySkipIfFreshWithinDays());
        LocalDate freshCutoff = end.minusDays(freshDays);
        var latest = repository.findTopBySymbolOrderByTimestampDesc(symbol.trim().toUpperCase(Locale.ROOT));
        if (latest.isPresent() && latest.get().getTimestamp() != null) {
            LocalDate lastDay = latest.get().getTimestamp().toLocalDate();
            if (!lastDay.isBefore(freshCutoff)) {
                log.debug("[ETF][HISTORY] skip symbol={} lastDay={} (fresh within {}d)", symbol, lastDay, freshDays);
                return;
            }
        }
        ingestHistory(symbol, periodDays);
    }

    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void ingestHistory(String symbol, int periodDays) {
        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(Math.max(1, periodDays));
            int inserted = 0;
            if (!useYahooOnlyForHistory(symbol)) {
                inserted = ingestHistoryFromFinnhub(symbol, start, end);
            }
            if (inserted < minRowsBeforeYahooFallback(periodDays)) {
                inserted += ingestHistoryFromYahoo(symbol, start, end, periodDays);
            }
            log.info("[ETF][HISTORY] Backfill done symbol={} inserted={} periodDays={}", symbol, inserted, periodDays);
        } catch (Exception e) {
            log.error("[ETF][HISTORY] Backfill failed symbol={} periodDays={}", symbol, periodDays, e);
        }
    }

    private boolean useYahooOnlyForHistory(String symbol) {
        if (etfProperties.isHistoryUseYahooOnly()) {
            return true;
        }
        String key = symbol.trim().toUpperCase(Locale.ROOT);
        if (finnhubHistoryNoDataSymbols.contains(key)) {
            return true;
        }
        List<String> configured = etfProperties.getHistoryYahooOnlySymbols();
        if (configured == null) {
            return false;
        }
        return configured.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .anyMatch(key::equals);
    }

    private static int minRowsBeforeYahooFallback(int periodDays) {
        return Math.max(40, Math.min(periodDays / 2, 180));
    }

    private int ingestHistoryFromFinnhub(String symbol, LocalDate start, LocalDate end) {
        long fromEpoch = start.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long toEpoch = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() - 1;
        FinHubCandleDto candles = finHubClient.fetchDailyCandles(symbol, fromEpoch, toEpoch).block();
        if (candles == null || !"ok".equalsIgnoreCase(candles.getS()) || candles.getT() == null || candles.getC() == null) {
            String key = symbol.trim().toUpperCase(Locale.ROOT);
            boolean firstTime = finnhubHistoryNoDataSymbols.add(key);
            if (firstTime) {
                log.info("[ETF][HISTORY] Finnhub has no daily candles for {} — Yahoo will be used for history", key);
            } else {
                log.debug("[ETF][HISTORY] Finnhub skip symbol={} (known no_data), using Yahoo", key);
            }
            return 0;
        }
        int inserted = 0;
        int size = Math.min(candles.getT().size(), candles.getC().size());
        for (int i = 0; i < size; i++) {
            Long epoch = candles.getT().get(i);
            Double close = candles.getC().get(i);
            if (epoch == null || close == null || close <= 0) {
                continue;
            }
            LocalDate day = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
            if (repository.existsForDay(symbol, day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
                continue;
            }
            BigDecimal mid = BigDecimal.valueOf(close);
            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(symbol);
            entity.setBuyPrice(SpreadCalculator.buyPrice(mid));
            entity.setSellPrice(SpreadCalculator.sellPrice(mid));
            entity.setSource(SOURCE_ETF);
            entity.setTimestamp(LocalDateTime.of(day, java.time.LocalTime.NOON));
            repository.save(entity);
            inserted++;
        }
        return inserted;
    }

    private int ingestHistoryFromYahoo(String symbol, LocalDate start, LocalDate end, int periodDays) {
        int inserted = 0;
        List<YahooChartClient.YahooDailyBar> bars = yahooChartClient.fetchDailyBars(symbol, periodDays >= 365 ? "1y" : "6mo");
        for (YahooChartClient.YahooDailyBar bar : bars) {
            LocalDate day = bar.day();
            if (day.isBefore(start) || day.isAfter(end)) {
                continue;
            }
            if (bar.close() == null || bar.close().signum() <= 0) {
                continue;
            }
            if (repository.existsForDay(symbol, day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
                continue;
            }
            BigDecimal mid = bar.close();
            MarketPriceHistory entity = new MarketPriceHistory();
            entity.setSymbol(symbol);
            entity.setBuyPrice(SpreadCalculator.buyPrice(mid));
            entity.setSellPrice(SpreadCalculator.sellPrice(mid));
            entity.setSource(SOURCE_ETF_YAHOO);
            entity.setTimestamp(LocalDateTime.of(day, java.time.LocalTime.NOON));
            repository.save(entity);
            inserted++;
        }
        return inserted;
    }
}